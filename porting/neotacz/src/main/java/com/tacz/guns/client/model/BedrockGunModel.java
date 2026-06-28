package com.tacz.guns.client.model;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.debug.ScopeRenderDebug;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.*;
import com.tacz.guns.client.model.listener.model.ModelAdditionalMagazineListener;
import com.tacz.guns.client.renderer.item.FirstPersonHandSway;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

import static com.tacz.guns.client.model.GunModelConstant.*;

public class BedrockGunModel extends BedrockAnimatedModel {
    public static final int SCOPE_GUN_CLIP_NONE = 0;

    protected final EnumMap<AttachmentType, List<BedrockPart>> refitAttachmentViewPath = Maps.newEnumMap(AttachmentType.class);
    private final EnumMap<AttachmentType, ItemStack> currentAttachmentItem = Maps.newEnumMap(AttachmentType.class);
    private final Set<String> adapterToRender = Sets.newHashSet();
    private final ArrayList<ShellRender> shellRenderList = new ArrayList<>();

    // 第一人称机瞄摄像机定位组的路径
    protected @Nullable List<BedrockPart> ironSightPath;
    // 第一人称idle状态摄像机定位组的路径
    protected @Nullable List<BedrockPart> idleSightPath;
    // 第三人称手部物品渲染原点定位组的路径
    protected @Nullable List<BedrockPart> thirdPersonHandOriginPath;
    // 展示框渲染原点定位组的路径
    protected @Nullable List<BedrockPart> fixedOriginPath;
    // 地面实体渲染原点定位组的路径
    protected @Nullable List<BedrockPart> groundOriginPath;
    // 瞄具配件定位组的路径。其他配件不需要存路径，只需要替换渲染。但是瞄具定位组需要用来辅助第一人称瞄准的摄像机定位。
    protected @Nullable List<BedrockPart> scopePosPath;
    // 枪口火焰定位组
    protected @Nullable List<BedrockPart> muzzleFlashPosPath;
    // 第一人称左手手臂定位组
    protected @Nullable List<BedrockPart> leftHandPosPath;
    // 第一人称右手手臂定位组
    protected @Nullable List<BedrockPart> rightHandPosPath;
    // 根组
    protected @Nullable BedrockPart root;
    // 弹匣定位组
    protected @Nullable BedrockPart magazineNode;
    // 换弹时第二个弹匣定位组
    protected @Nullable BedrockPart additionalMagazineNode;
    protected @Nullable List<BedrockPart> laserBeamPaths;

    private boolean renderHand = true;
    private boolean renderMount;
    private ItemStack currentGunItem;
    private int currentExtendMagLevel = 0;

    public BedrockGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);

        this.magazineNode = Optional.ofNullable(modelMap.get(MAG_NORMAL_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);
        this.additionalMagazineNode = Optional.ofNullable(modelMap.get(MAG_ADDITIONAL_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);

        // 左手手臂
        this.setFunctionalRenderer(LEFTHAND_POS_NODE, bedrockPart -> new LeftHandRender(this));
        // 右手手臂
        this.setFunctionalRenderer(RIGHTHAND_POS_NODE, bedrockPart -> new RightHandRender(this));
        // 枪口火焰
        this.setFunctionalRenderer(MUZZLE_FLASH_ORIGIN_NODE, bedrockPart -> new MuzzleFlashRender(this));
        // 枪管内的子弹，用于闭膛待机枪械
        this.setFunctionalRenderer(BULLET_IN_BARREL, bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.hasBulletInBarrel(currentGunItem)));
        // 弹匣内子弹
        this.setFunctionalRenderer(BULLET_IN_MAG, bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.getCurrentAmmoCount(currentGunItem) > 0));
        // 机枪弹链
        this.setFunctionalRenderer(BULLET_CHAIN, bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.getCurrentAmmoCount(currentGunItem) > 0));
        // 有通用瞄具时显示，用于放瞄具的导轨（如 AKM 的导轨）
        this.setFunctionalRenderer(MOUNT, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem != null && !scopeItem.isEmpty() && renderMount));
        // 无瞄具时可见，通常用于 M4 上
        this.setFunctionalRenderer(CARRY, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem == null || scopeItem.isEmpty()));
        // 有瞄具时显示，折叠的机械瞄具
        this.setFunctionalRenderer(SIGHT_FOLDED, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem != null && !scopeItem.isEmpty()));
        // 无瞄具时可见，机械瞄具
        this.setFunctionalRenderer(SIGHT, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem == null || scopeItem.isEmpty()));
        // 安装一级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_1, bedrockPart -> extendedMagHiddenRender(bedrockPart, 1));
        // 安装二级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_2, bedrockPart -> extendedMagHiddenRender(bedrockPart, 2));
        // 安装三级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_3, bedrockPart -> extendedMagHiddenRender(bedrockPart, 3));
        // 没有安装扩容弹匣时显示
        this.setFunctionalRenderer(MAG_STANDARD, bedrockPart -> extendedMagHiddenRender(bedrockPart, 0));
        // 部分枪械换弹动画播放时，会同时出现两个弹匣，这个就是程序自动渲染另一个弹匣的代码
        this.setFunctionalRenderer(MAG_ADDITIONAL_NODE, this::renderAdditionalMagazine);
        // 默认护木渲染
        this.setFunctionalRenderer(HANDGUARD_DEFAULT_NODE, this::handguardDefaultRender);
        // 战术护木渲染
        this.setFunctionalRenderer(HANDGUARD_TACTICAL_NODE, this::handguardTacticalRender);
        // 缓存其他定位组
        this.cacheOtherPath();
        // 缓存改装 UI 下各个配件的特写视角定位组
        this.cacheRefitAttachmentViewPath();
        // 缓存抛壳窗
        this.cacheShellOriginNodes();
        // 准备各个配件的渲染
        this.allAttachmentRender();
        // 配件转接口渲染
        this.setFunctionalRenderer(ATTACHMENT_ADAPTER_NODE, this::attachmentAdapterNodeRender);
    }

    private void cacheOtherPath() {
        ironSightPath = getPath(modelMap.get(IRON_VIEW_NODE));
        idleSightPath = getPath(modelMap.get(IDLE_VIEW_NODE));
        thirdPersonHandOriginPath = getPath(modelMap.get(THIRD_PERSON_HAND_ORIGIN_NODE));
        fixedOriginPath = getPath(modelMap.get(FIXED_ORIGIN_NODE));
        groundOriginPath = getPath(modelMap.get(GROUND_ORIGIN_NODE));
        muzzleFlashPosPath = getPath(modelMap.get(MUZZLE_FLASH_ORIGIN_NODE));
        leftHandPosPath = getPath(modelMap.get(LEFTHAND_POS_NODE));
        rightHandPosPath = getPath(modelMap.get(RIGHTHAND_POS_NODE));
        scopePosPath = getPath(modelMap.get(AttachmentType.SCOPE.name().toLowerCase() + ATTACHMENT_POS_SUFFIX));
        laserBeamPaths = getPath(modelMap.get("laser_beam"));
        root = Optional.ofNullable(modelMap.get(ROOT_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);
    }

    private void cacheRefitAttachmentViewPath() {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                refitAttachmentViewPath.put(type, getPath(modelMap.get(REFIT_VIEW_NODE)));
                continue;
            }
            String nodeName = REFIT_VIEW_PREFIX + type.name().toLowerCase() + REFIT_VIEW_SUFFIX;
            refitAttachmentViewPath.put(type, getPath(modelMap.get(nodeName)));
        }
    }

    private void cacheShellOriginNodes() {
        ModelRendererWrapper rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE);
        int i = 1;
        while (rendererWrapper != null) {
            ShellRender shellRender = new ShellRender(this);
            this.setFunctionalRenderer(rendererWrapper.getModelRenderer().name, bedrockPart -> shellRender);
            shellRenderList.add(shellRender);
            rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE_PREFIX + i);
            i++;
        }
    }

    @Nullable
    private IFunctionalRenderer attachmentAdapterNodeRender(BedrockPart bedrockPart) {
        for (BedrockPart child : bedrockPart.children) {
            if (child.name == null) {
                child.visible = false;
                continue;
            }
            child.visible = adapterToRender.contains(child.name);
        }
        return null;
    }

    private void allAttachmentRender() {
        for (AttachmentType type : AttachmentType.values()) {
            // 瞄具的渲染需要提前
            if (type == AttachmentType.NONE || type == AttachmentType.SCOPE) {
                continue;
            }
            String positionNodeName = type.name().toLowerCase() + ATTACHMENT_POS_SUFFIX;
            String defaultNodeName = type.name().toLowerCase() + DEFAULT_ATTACHMENT_SUFFIX;
            this.setFunctionalRenderer(positionNodeName, bedrockPart -> {
                bedrockPart.visible = false;
                return new AttachmentRender(this, type);
            });
            this.setFunctionalRenderer(defaultNodeName, bedrockPart -> {
                ItemStack attachmentItem = currentAttachmentItem.get(type);
                if (type == AttachmentType.MUZZLE && checkShowMuzzle(bedrockPart, attachmentItem)) {
                    return null;
                }
                bedrockPart.visible = attachmentItem == null || attachmentItem.isEmpty();
                return null;
            });
        }
    }

    private static boolean checkShowMuzzle(BedrockPart bedrockPart, ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment != null) {
            Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
            var attachmentIndex = TimelessAPI.getClientAttachmentIndex(attachmentId);
            if (attachmentIndex.isPresent()) {
                bedrockPart.visible = attachmentIndex.get().isShowMuzzle();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private IFunctionalRenderer handguardTacticalRender(BedrockPart bedrockPart) {
        ItemStack laserItem = currentAttachmentItem.get(AttachmentType.LASER);
        ItemStack gripItem = currentAttachmentItem.get(AttachmentType.GRIP);
        bedrockPart.visible = !laserItem.isEmpty() || !gripItem.isEmpty();
        return null;
    }

    @Nullable
    private IFunctionalRenderer handguardDefaultRender(BedrockPart bedrockPart) {
        ItemStack laserItem = currentAttachmentItem.get(AttachmentType.LASER);
        ItemStack gripItem = currentAttachmentItem.get(AttachmentType.GRIP);
        bedrockPart.visible = laserItem.isEmpty() && gripItem.isEmpty();
        return null;
    }

    @NotNull
    private IFunctionalRenderer renderAdditionalMagazine(BedrockPart bedrockPart) {
        return (poseStack, vertexBuffer, transformType, light, overlay) -> {
            if (bedrockPart.visible) {
                bedrockPart.compile(poseStack.last(), vertexBuffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                for (BedrockPart part : bedrockPart.children) {
                    part.render(poseStack, transformType, vertexBuffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                }
                if (magazineNode != null && magazineNode.visible) {
                    magazineNode.compile(poseStack.last(), vertexBuffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                    for (BedrockPart part : magazineNode.children) {
                        part.render(poseStack, transformType, vertexBuffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
            }
        };
    }

    /**
     * 添加枪械自定义的文本显示
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) -> this.setFunctionalRenderer(name, bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
    }

    public void render(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        if (!prepareRenderState(gunItem)) {
            return;
        }
        if (laserBeamPaths != null) {
            BeamRenderer.renderLaserBeam(gunItem, matrixStack, transformType, laserBeamPaths);
        }

		if (ARCompat.shouldAccelerate()) {
			renderAccelerated(matrixStack, gunItem, transformType, renderType, light, overlay);
			return;
		}

        // 镜子需要先渲染，写入模板值
        ItemStack attachmentItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (scopePosPath != null && attachmentItem != null && !attachmentItem.isEmpty()) {
            matrixStack.pushPose();
            for (BedrockPart bedrockPart : scopePosPath) {
                bedrockPart.translateAndRotateAndScale(matrixStack);
            }
            AttachmentRender.renderAttachment(attachmentItem, currentGunItem, matrixStack, transformType, light, overlay);
            matrixStack.popPose();
            // 开启模板测试，因为镜内不渲染枪体
            boolean stencilEnabled = false;
            if (iAttachment != null) {
                Optional<ClientAttachmentIndex> attachmentIndex = TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
                if (attachmentIndex.isPresent()) {
                    ClientAttachmentIndex index = attachmentIndex.get();
                    if (index.isScope() && index.isSight()) { // 组合镜
                        RenderHelper.enableItemEntityStencilTest();
                        RenderHelper.stencilFunc(GL11.GL_GREATER, 127, 0xFF);
                        stencilEnabled = true;
                    } else if (index.isScope()) { // 长筒镜
                        RenderHelper.enableItemEntityStencilTest();
                        RenderHelper.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
                        stencilEnabled = true;
                    }
                }
            }
            try {
                if (stencilEnabled) {
                    RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                }
                super.render(matrixStack, transformType, renderType, light, overlay);
            } finally {
                if (stencilEnabled) {
                    RenderHelper.disableItemEntityStencilTest();
                    RenderHelper.clearStencilBuffer();
                }
            }
            return;
        }
        super.render(matrixStack, transformType, renderType, light, overlay);
    }

    public void renderToBuffer(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType, VertexConsumer buffer, int light, int overlay) {
        if (!prepareRenderState(gunItem)) {
            return;
        }
        boolean suppressFunctionalExtras = RenderHelper.areDeferredRenderersSuppressed();
        boolean collectingDeferredRenderers = RenderHelper.isCollectingDeferredFunctionalRenderers();
        // Submit callbacks still need the full non-accelerated gun render side effects owned by render(...).
        if (!suppressFunctionalExtras && laserBeamPaths != null) {
            BeamRenderer.renderLaserBeam(gunItem, matrixStack, transformType, laserBeamPaths);
        }

        ItemStack attachmentItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        boolean hasMountedScope = scopePosPath != null && attachmentItem != null && !attachmentItem.isEmpty();
        ScopeRenderDebug.path("gun_model_render_to_buffer", attachmentItem, currentGunItem, transformType,
                "collecting=" + collectingDeferredRenderers + ",mounted=" + hasMountedScope + ",scopePos=" + (scopePosPath != null));
        if (collectingDeferredRenderers && hasMountedScope && !transformType.firstPerson()) {
            matrixStack.pushPose();
            for (BedrockPart bedrockPart : scopePosPath) {
                bedrockPart.translateAndRotateAndScale(matrixStack);
            }
            AttachmentRender.submitMountedAttachment(attachmentItem, currentGunItem, matrixStack, transformType, light, overlay);
            matrixStack.popPose();
        }

        if (!collectingDeferredRenderers && hasMountedScope) {
            // MC 26.2 retained custom geometry records vertices first and draws later from RenderType
            // state. GL stencil calls here would not clip the later gun draw, so first-person
            // mounted scope masking is handled by ScopeStencilFeatureRenderer's integrated
            // scope+gun feature pass.
            super.renderToBuffer(matrixStack, transformType, buffer, light, overlay);
            return;
        }
        super.renderToBuffer(matrixStack, transformType, buffer, light, overlay);
    }

    public void collectDeferredFunctionalRenderers(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType, int light, int overlay) {
        renderToBuffer(matrixStack, gunItem, transformType, RenderHelper.noopVertexConsumer(), light, overlay);
    }

    public boolean submitFirstPersonScopeStencilPass(SubmitNodeCollector collector, PoseStack matrixStack,
                                                     AbstractClientPlayer player, ItemStack gunItem,
                                                     ItemDisplayContext transformType, RenderType gunRenderType,
                                                     Identifier gunTexture, int light, int overlay, int order,
                                                     FirstPersonHandSway handSway, boolean renderHandForCallback) {
        if (collector == null || !transformType.firstPerson() || !prepareRenderState(gunItem)) {
            return false;
        }
        ItemStack attachmentItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (scopePosPath == null || attachmentItem == null || attachmentItem.isEmpty() || iAttachment == null) {
            return false;
        }
        Optional<ClientAttachmentIndex> attachmentIndexOptional = TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
        if (attachmentIndexOptional.isEmpty()) {
            return false;
        }
        ClientAttachmentIndex attachmentIndex = attachmentIndexOptional.get();
        BedrockAttachmentModel attachmentModel = attachmentIndex.getAttachmentModel();
        Identifier attachmentTexture = attachmentIndex.getModelTexture();
        if (attachmentModel == null || attachmentTexture == null || (!attachmentIndex.isScope() && !attachmentIndex.isSight())) {
            ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex,
                    attachmentTexture, attachmentModel != null, false, "missing_scope_stencil_model_or_texture");
            return false;
        }
        if (!ScopeStencilFeatureRenderer.canStageIntegratedPass(attachmentModel)) {
            ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex,
                    attachmentTexture, true, false, "integrated_scope_gun_missing_required_paths");
            return false;
        }

        RenderType attachmentRenderType = RenderTypes.entityCutout(attachmentTexture);
        int activeScopeViewIndex = resolveActiveScopeViewIndex(iAttachment, attachmentItem, attachmentIndex);
        ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex,
                attachmentTexture, true, true, "integrated_scope_gun_feature");

        OrderedSubmitNodeCollector orderedCollector = collector.order(order - 100_000);
        if (!(orderedCollector instanceof SubmitNodeCollection collection)) {
            ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex,
                    attachmentTexture, true, false, "scope_stencil_feature_collector_unavailable");
            return false;
        }

        PoseStack gunPose = copyPose(matrixStack);

        if (collection.allPhases().isEmpty()) {
            ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex,
                    attachmentTexture, true, false, "scope_stencil_feature_phase_unavailable");
            return false;
        }

        @SuppressWarnings("unchecked")
        FeatureRenderPhase<SubmitNode> scopePhase = (FeatureRenderPhase<SubmitNode>) collection.allPhases().get(0);
        scopePhase.submit(new ScopeStencilFeatureRenderer.ScopeSubmit(
                this,
                attachmentModel,
                player,
                gunItem,
                attachmentItem,
                transformType,
                gunRenderType,
                attachmentRenderType,
                gunTexture,
                attachmentTexture,
                new Matrix4f(gunPose.last().pose()),
                new Matrix3f(gunPose.last().normal()),
                handSway,
                renderHandForCallback,
                activeScopeViewIndex,
                light,
                overlay
        ));
        return true;
    }

    private static int resolveActiveScopeViewIndex(IAttachment attachment, ItemStack attachmentItem,
                                                   ClientAttachmentIndex attachmentIndex) {
        int[] views = attachmentIndex.getViews();
        if (views.length == 0) {
            return -1;
        }
        int zoomNumber = attachment.getZoomNumber(attachmentItem);
        return views[Math.floorMod(zoomNumber, views.length)] - 1;
    }

    void renderGunBodyToBuffer(PoseStack matrixStack, ItemDisplayContext transformType, VertexConsumer buffer,
                               int light, int overlay) {
        super.renderToBuffer(matrixStack, transformType, buffer, light, overlay);
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private boolean prepareRenderState(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return false;
        }
        currentGunItem = gunItem;
        currentExtendMagLevel = 0;
        adapterToRender.clear();
        // 更新配件物品的缓存，以供渲染使用
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ItemStack attachmentItem = iGun.getAttachment(gunItem, type);
            if (attachmentItem.isEmpty()) {
                attachmentItem = iGun.getBuiltinAttachment(gunItem, type);
            }
            currentAttachmentItem.put(type, attachmentItem);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment != null) {
                TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(attachmentItem)).ifPresent(index -> {
                    // 读取扩容等级，为扩容弹匣渲染做准备
                    if (type == AttachmentType.EXTENDED_MAG) {
                        currentExtendMagLevel = index.getData().getExtendedMagLevel();
                    }
                    // 读取瞄具 Mount 的渲染需求
                    if (type == AttachmentType.SCOPE) {
                        renderMount = index.isShowMount();
                    }
                    // 添加需要渲染的转接口
                    if (index.getAdapterNodeName() != null) {
                        adapterToRender.add(index.getAdapterNodeName());
                    }
                });
            }
        }
        return true;
    }

	public void renderAccelerated(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
		// 镜子需要先渲染，写入模板值
		var attachmentItem = currentAttachmentItem.get(AttachmentType.SCOPE);
		var iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
		var useStencil = false;

		if (scopePosPath != null && attachmentItem != null && !attachmentItem.isEmpty()) {
			matrixStack.pushPose();

			for (BedrockPart bedrockPart : scopePosPath) {
				bedrockPart.translateAndRotateAndScale(matrixStack);
			}

			AttachmentRender.renderAttachment(attachmentItem, currentGunItem, matrixStack, transformType, light, overlay);
			matrixStack.popPose();

			// 开启模板测试，因为镜内不渲染枪体
			if (iAttachment != null) {
				var attachmentIndex = TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));

				// 这里不用ifPresent是因为需要设置useStencil, lambda无法设置局部变量
				if (attachmentIndex.isPresent()) {
					var index = attachmentIndex.get();
					if (index.isScope()) {
						// 如果有attachment, 则设置层前任务开启模板缓冲区设置对应的模板函数
						ARCompat.setRenderBeforeFunction(() -> {
							if (index.isSight()) { // 组合镜
								RenderHelper.enableItemEntityStencilTest();
								RenderHelper.stencilFunc(GL11.GL_GREATER, 127, 0xFF);
							} else { // 长筒镜
								RenderHelper.enableItemEntityStencilTest();
								RenderHelper.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
							}

							// 设置不改变任何模板值, 这里本来是无论是否有attachment都要执行的, 但是如果不执行到这里模板测试自然不会开启
							// 也就无论如何都不会改变模板值, 所以一同在此处设置应该也没有问题
							RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
						});

						// 确认使用层前行为, 应在渲染完毕后重置层前行为
						useStencil = true;
					}
				}
			}
		}

		ARCompat.setRenderLayer(-943 + 3);

		if (useStencil) {
			// 设置层后任务
			ARCompat.setRenderAfterFunction(() -> {
				// 关闭模板测试
				RenderHelper.disableItemEntityStencilTest();
				// 重置模板缓冲区
				RenderHelper.clearStencilBuffer();
			});
		}

		try {
			super.render(matrixStack, transformType, renderType, light, overlay);
		} finally {
			// 重置层和层后任务, 还原现场
			ARCompat.resetRenderLayer();

			// 如果使用了层前/层后行为, 则进行重置, 还原现场
			if (useStencil) {
				ARCompat.resetRenderBeforeFunction();
				ARCompat.resetRenderAfterFunction();
			}
		}
	}

    @Nullable
    private IFunctionalRenderer ammoHiddenRender(BedrockPart bedrockPart, Predicate<IGun> predicate) {
        IGun iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun != null) {
            bedrockPart.visible = predicate.test(iGun);
        }
        return null;
    }

    @Nullable
    private IFunctionalRenderer scopeHiddenRender(BedrockPart bedrockPart, Predicate<ItemStack> predicate) {
        // 安装瞄具时可见
        ItemStack scopeItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        bedrockPart.visible = predicate.test(scopeItem);
        return null;
    }

    @Nullable
    private IFunctionalRenderer extendedMagHiddenRender(BedrockPart bedrockPart, int level) {
        bedrockPart.visible = currentExtendMagLevel == level;
        return null;
    }

    @Override
    public AnimationListener supplyListeners(String nodeName, ObjectAnimationChannel.ChannelType type) {
        AnimationListener listener = super.supplyListeners(nodeName, type);
        if (listener == null) {
            return null;
        }
        if (nodeName.equals(MAG_ADDITIONAL_NODE)) {
            // 额外弹匣只有当动画中有它的关键帧的时候才渲染
            return new ModelAdditionalMagazineListener(listener, this);
        }
        return listener;
    }

    @Override
    public void cleanAnimationTransform() {
        super.cleanAnimationTransform();
        if (additionalMagazineNode != null) {
            additionalMagazineNode.visible = false;
        }
    }

    public EnumMap<AttachmentType, ItemStack> getCurrentAttachmentItem() {
        return currentAttachmentItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    @Nullable
    public BedrockPart getAdditionalMagazineNode() {
        return additionalMagazineNode;
    }

    @Nullable
    public List<BedrockPart> getIronSightPath() {
        return ironSightPath;
    }

    @Nullable
    public List<BedrockPart> getIdleSightPath() {
        return idleSightPath;
    }

    @Nullable
    public List<BedrockPart> getThirdPersonHandOriginPath() {
        return thirdPersonHandOriginPath;
    }

    @Nullable
    public List<BedrockPart> getFixedOriginPath() {
        return fixedOriginPath;
    }

    @Nullable
    public List<BedrockPart> getGroundOriginPath() {
        return groundOriginPath;
    }

    @Nullable
    public List<BedrockPart> getMuzzleFlashPosPath() {
        return muzzleFlashPosPath;
    }

    @Nullable
    public List<BedrockPart> getLeftHandPosPath() {
        return leftHandPosPath;
    }

    @Nullable
    public List<BedrockPart> getRightHandPosPath() {
        return rightHandPosPath;
    }

    @Nullable
    public List<BedrockPart> getScopePosPath() {
        return scopePosPath;
    }

    @Nullable
    public List<BedrockPart> getRefitAttachmentViewPath(AttachmentType type) {
        return refitAttachmentViewPath.get(type);
    }

    @Nullable
    public ShellRender getShellRender(int index) {
        if (index < 0 || index >= shellRenderList.size()) {
            return null;
        }
        return shellRenderList.get(index);
    }

    @Nullable
    public BedrockPart getRootNode() {
        return root;
    }

    public boolean getRenderHand() {
        return renderHand;
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }
}
