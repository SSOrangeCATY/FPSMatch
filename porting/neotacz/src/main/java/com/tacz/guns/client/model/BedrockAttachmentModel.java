package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.debug.ScopeRenderDebug;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.model.functional.TextShowRender;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BedrockAttachmentModel extends BedrockAnimatedModel {
    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String SCOPE_BODY_NODE = "scope_body";
    private static final String OCULAR_RING_NODE = "ocular_ring";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final Pattern LASER_BEAM_PATTERN = Pattern.compile("^laser_beam(_(\\d+))?$");

    protected List<List<BedrockPart>> scopeViewPaths;
    protected @Nullable List<BedrockPart> scopeBodyPath;
    protected @Nullable List<BedrockPart> ocularRingPath;
    protected List<List<BedrockPart>> ocularNodePaths;
    protected List<Boolean> isScopeOcular;
    protected List<List<BedrockPart>> divisionNodePaths;
    protected @Nullable List<List<BedrockPart>> laserBeamPaths;

    private @Nullable ItemStack currentGunItem;
    private @Nullable ItemStack attachmentItem;

    private boolean isScope = false;
    private boolean isSight = false;
    private float scopeViewRadiusModifier = 1;

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        scopeViewPaths = new ArrayList<>();
        ocularNodePaths = new ArrayList<>();
        isScopeOcular = new ArrayList<>();
        divisionNodePaths = new ArrayList<>();
        laserBeamPaths = new ArrayList<>();
        // 初始化 view 的 node path
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int i = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + i++));
        }
        // 初始化 ocular 的 node path
        String ocularRegex = "^(" + OCULAR_NODE + "|" + OCULAR_SIGHT_NODE + "|" + OCULAR_SCOPE_NODE + ")(_(\\d+))?$";
        Pattern ocularPattern = Pattern.compile(ocularRegex);
        TreeMap<Integer, OcularWrapper> map = new TreeMap<>();
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            Matcher matcher = ocularPattern.matcher(entry.getKey());
            if (matcher.matches()) {
                int num = 1;
                String numStr = matcher.group(3);
                if (numStr != null) {
                    num = Integer.parseInt(numStr);
                }
                String type = matcher.group(1);
                boolean isScope = OCULAR_SCOPE_NODE.equals(type);
                map.put(num, new OcularWrapper(entry.getValue(), isScope));
            }
            if (LASER_BEAM_PATTERN.matcher(entry.getKey()).find()) {
                laserBeamPaths.add(getPath(entry.getValue()));
            }
        }
        for (OcularWrapper wrapper : map.values()) {
            ocularNodePaths.add(getPath(wrapper.renderer));
            isScopeOcular.add(wrapper.isScope);
        }
        // 初始化 division 的 node path
        ModelRendererWrapper divisionModel = modelMap.get(DIVISION_NODE);
        path = getPath(modelMap.get(DIVISION_NODE));
        i = 2;
        while (path != null) {
            divisionNodePaths.add(path);
            divisionModel.setHidden(true);
            divisionModel = modelMap.get(DIVISION_NODE + '_' + i++);
            path = getPath(divisionModel);
        }

        scopeBodyPath = getPath(modelMap.get(SCOPE_BODY_NODE));
        ocularRingPath = getPath(modelMap.get(OCULAR_RING_NODE));
    }

    @Nullable
    public List<BedrockPart> getScopeViewPath(int viewSwitchCount) {
        if (scopeViewPaths.isEmpty()) {
            return null;
        }
        if (viewSwitchCount >= scopeViewPaths.size()) {
            return scopeViewPaths.get(0);
        }
        return scopeViewPaths.get(viewSwitchCount);
    }

    public int getScopeViewPathCount() {
        return scopeViewPaths.size();
    }

    boolean isScopeOcularViewIndex(int viewIndex) {
        if (viewIndex < 0 || viewIndex >= isScopeOcular.size()) {
            return isScope;
        }
        return isScopeOcular.get(viewIndex);
    }

    public void setIsScope(boolean isScope) {
        this.isScope = isScope;
    }

    public void setIsSight(boolean isSight) {
        this.isSight = isSight;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public void setScopeViewRadiusModifier(float scopeViewRadiusModifier) {
        this.scopeViewRadiusModifier = scopeViewRadiusModifier;
    }

    /**
     * 添加枪械自定义的文本显示
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) -> this.setFunctionalRenderer(name,
                bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
    }

    public void render(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        this.currentGunItem = currentGunItem;
        this.attachmentItem = attachmentItem;
        if (transformType.firstPerson()) {
            if (isScope && isSight) {
                renderBoth(matrixStack, transformType, renderType, light, overlay);
            } else if (isScope) {
                renderScope(matrixStack, transformType, renderType, light, overlay);
            } else if (isSight) {
                renderSight(matrixStack, transformType, renderType, light, overlay);
            }
        } else {
            if (scopeBodyPath != null) {
                renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
            }
            if (ocularRingPath != null) {
                renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
            }
        }
        if (!isScope && !isSight && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry);
            }
        }
        super.render(matrixStack, transformType, renderType, light, overlay);
        if ((isScope || isSight) && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry);
            }
        }
    }

    void prepareInstalledRenderState(@Nullable ItemStack attachmentItem, ItemStack currentGunItem) {
        this.currentGunItem = currentGunItem;
        this.attachmentItem = attachmentItem;
    }

    public int submitInstalled(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack,
                                SubmitNodeCollector collector, ItemDisplayContext transformType, RenderType renderType,
                                Identifier texture, int light, int overlay) {
        this.currentGunItem = currentGunItem;
        this.attachmentItem = attachmentItem;
        if (transformType.firstPerson()) {
            submitPlainInstalled(matrixStack, collector, transformType, renderType, light, overlay);
        } else {
            submitPlainInstalled(matrixStack, collector, transformType, renderType, light, overlay);
        }
        return BedrockGunModel.SCOPE_GUN_CLIP_NONE;
    }
    private void submitPlainInstalled(PoseStack matrixStack, SubmitNodeCollector collector, ItemDisplayContext transformType,
                                      RenderType renderType, int light, int overlay) {
        if (!transformType.firstPerson()) {
            submitPlainPartStage("scope_body_plain", matrixStack, collector, renderType, transformType, light, overlay,
                    scopeBodyPath, false, getClientAimingProgress(), getScopeApertureRadius());
            submitPlainPartStage("ocular_ring_plain", matrixStack, collector, renderType, transformType, light, overlay,
                    ocularRingPath, false, getClientAimingProgress(), getScopeApertureRadius());
        }
        if (!isScope && !isSight && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry);
            }
        }
        submitModelStage("plain_model", matrixStack, collector, renderType, transformType, light, overlay);
        if ((isScope || isSight) && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry);
            }
        }
    }
    Vector3f getBedrockPartCenter(PoseStack poseStack, @Nonnull List<BedrockPart> path) {
        poseStack.pushPose();
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(poseStack);
        }
        Vector3f result = new Vector3f(poseStack.last().pose().m30(), poseStack.last().pose().m31(), poseStack.last().pose().m32());
        poseStack.popPose();
        return result;
    }
    private void renderTempPart(PoseStack poseStack, ItemDisplayContext transformType, RenderType renderType,
                                int light, int overlay, @Nonnull List<BedrockPart> path) {
        poseStack.pushPose();
        for (int i = 0; i < path.size() - 1; ++i) {
            path.get(i).translateAndRotateAndScale(poseStack);
        }
        BedrockPart part = path.get(path.size() - 1);
        part.visible = true;
        SubmitNodeCollector collector = RenderHelper.currentSubmitNodeCollector();
        if (collector != null) {
            BedrockSubmitUtils.submitPart(collector, poseStack, renderType, part, transformType, light, overlay);
        }
        part.visible = false;
        poseStack.popPose();
    }
    void renderTempPartToBuffer(PoseStack poseStack, ItemDisplayContext transformType, VertexConsumer buffer,
                                int light, int overlay, @Nonnull List<BedrockPart> path) {
        poseStack.pushPose();
        for (int i = 0; i < path.size() - 1; ++i) {
            path.get(i).translateAndRotateAndScale(poseStack);
        }
        BedrockPart part = path.get(path.size() - 1);
        part.visible = true;
        part.render(poseStack, transformType, buffer, light, overlay);
        part.visible = false;
        poseStack.popPose();
    }
    private void renderOcularStencil(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay, boolean isScope) {
        if (!ocularNodePaths.isEmpty()) {
            RenderHelper.colorMask(false, false, false, false);
            RenderHelper.depthMask(false);
            RenderHelper.stencilMask(0xFF);
            RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            try {
                // 绘制目镜
                for (int i = ocularNodePaths.size() - 1; i >= 0; i--) {
                    if (isScope == isScopeOcular.get(i)) {
                        RenderHelper.stencilFunc(GL11.GL_GREATER, i + 1, 0xFF);
                        renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularNodePaths.get(i));
                    }
                }
            } finally {
                // 恢复渲染状态
                RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                RenderHelper.depthMask(true);
                RenderHelper.colorMask(true, true, true, true);
            }
        }
    }
    private void submitPlainPartStage(String stage, PoseStack matrixStack, SubmitNodeCollector collector,
                                      RenderType renderType, ItemDisplayContext transformType, int light, int overlay,
                                      @Nullable List<BedrockPart> path, boolean selective, float aimingProgress,
                                      float apertureRadius) {
        if (path == null) {
            ScopeRenderDebug.stage(stage, attachmentItem, currentGunItem, transformType, isScope, isSight, selective,
                    aimingProgress, apertureRadius, "none", "none", false, "missing_part_path");
            return;
        }
        ScopeRenderDebug.stage(stage, attachmentItem, currentGunItem, transformType, isScope, isSight, selective,
                aimingProgress, apertureRadius, "none", "none", true, "");
        RenderHelper.submitCustomGeometry(collector, matrixStack, renderType, (pose, buffer) -> {
            PoseStack callbackPoseStack = BedrockSubmitUtils.fromPose(pose);
            renderTempPartToBuffer(callbackPoseStack, transformType, buffer, light, overlay, path);
        });
    }
    private void submitModelStage(String stage, PoseStack matrixStack, SubmitNodeCollector collector, RenderType renderType,
                                  ItemDisplayContext transformType, int light, int overlay) {
        ScopeRenderDebug.stage(stage, attachmentItem, currentGunItem, transformType, isScope, isSight, false,
                getClientAimingProgress(), getScopeApertureRadius(), "none", "none", true, "");
        RenderHelper.submitCustomGeometry(collector, matrixStack, renderType, (pose, buffer) -> {
            PoseStack callbackPoseStack = BedrockSubmitUtils.fromPose(pose);
            renderBaseModelToBuffer(callbackPoseStack, transformType, buffer, light, overlay);
        });
    }
    void renderBaseModelToBuffer(PoseStack matrixStack, ItemDisplayContext transformType, VertexConsumer buffer,
                                 int light, int overlay) {
        super.renderToBuffer(matrixStack, transformType, buffer, light, overlay);
    }

    void renderBaseModelToBuffer(PoseStack matrixStack, ItemDisplayContext transformType, VertexConsumer buffer,
                                 int light, int overlay, Set<BedrockPart> submittedVisibleSpecialLeaves) {
        if (transformType.firstPerson() && (isScope || isSight) && !submittedVisibleSpecialLeaves.isEmpty()) {
            // Retained stencil stages can be rejected later by GPU state ordering. Only filter a special leaf
            // after its ordinary visible fallback was submitted, so a failed stencil path cannot leave only the ring.
            renderBaseModelFilteredToBuffer(matrixStack, transformType, buffer, light, overlay, submittedVisibleSpecialLeaves);
        } else {
            super.renderToBuffer(matrixStack, transformType, buffer, light, overlay);
        }
    }

    private void renderBaseModelFilteredToBuffer(PoseStack matrixStack, ItemDisplayContext transformType,
                                                 VertexConsumer buffer, int light, int overlay,
                                                 Set<BedrockPart> excluded) {
        matrixStack.pushPose();
        for (BedrockPart model : shouldRender) {
            renderPartFiltered(matrixStack, transformType, buffer, light, overlay, model, excluded);
        }
        matrixStack.popPose();
        for (IFunctionalRenderer renderer : delegateRenderers) {
            renderer.render(matrixStack, buffer, transformType, light, overlay);
        }
        delegateRenderers = new ArrayList<>();
    }
    private static void renderPartFiltered(PoseStack poseStack, ItemDisplayContext transformType, VertexConsumer consumer,
                                           int light, int overlay, BedrockPart part, Set<BedrockPart> excluded) {
        if (!part.visible || excluded.contains(part)) {
            return;
        }
        int cubePackedLight = part.illuminated ? LightCoordsUtil.pack(15, 15) : light;
        if (part.cubes.isEmpty() && part.children.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        part.translateAndRotateAndScale(poseStack);
        part.compile(poseStack.last(), consumer, cubePackedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        for (BedrockPart child : part.children) {
            renderPartFiltered(poseStack, transformType, consumer, cubePackedLight, overlay, child, excluded);
        }
        poseStack.popPose();
    }
    private static void addSpecialLeaves(Set<BedrockPart> excluded, @Nullable List<List<BedrockPart>> paths) {
        if (paths == null) {
            return;
        }
        for (List<BedrockPart> path : paths) {
            addSpecialLeaf(excluded, path);
        }
    }
    private static void addSpecialLeaf(Set<BedrockPart> excluded, @Nullable List<BedrockPart> path) {
        if (path != null && !path.isEmpty()) {
            excluded.add(path.get(path.size() - 1));
        }
    }
    private void renderDivisionOnly(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        if (!divisionNodePaths.isEmpty()) {
            RenderHelper.disableDepthTest();
            try {
                for (int i = 0; i < divisionNodePaths.size(); i++) {
                    RenderHelper.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                    renderTempPart(matrixStack, transformType, renderType, light, overlay, divisionNodePaths.get(i));
                }
            } finally {
                RenderHelper.enableDepthTest();
            }
        }
    }
    private void renderOcularAndDivision(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay, boolean selective) {
        if (!ocularNodePaths.isEmpty()) {
            // 准备渲染圆形模板层
            RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);
            RenderHelper.colorMask(false, false, false, false);
            RenderHelper.depthMask(false);
            float rad = getScopeApertureRadius();
            try {
                for (int i = 0; i < ocularNodePaths.size(); i++) {
                    if (selective && !isScopeOcular.get(i)) {
                        continue;
                    }
                    ScopeRenderDebug.stage("aperture_fan", attachmentItem, currentGunItem, transformType, isScope, isSight,
                            selective, getClientAimingProgress(), rad, "legacy_immediate", "not_submitted",
                            false, "legacy_immediate_scope_fan_not_emitted");
                }
            } finally {
                RenderHelper.depthMask(true);
                RenderHelper.colorMask(true, true, true, true);
                RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            }
            for (int i = 0; i < ocularNodePaths.size() && i < divisionNodePaths.size(); i++) {
                if (i > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("Index of oculus is out of range for 127");
                }
                if (selective && !isScopeOcular.get(i)) {
                    RenderHelper.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                    renderTempPart(matrixStack, transformType, renderType, light, overlay, divisionNodePaths.get(i));
                } else {
                    // 渲染目镜黑色遮罩
                    RenderHelper.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                    renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularNodePaths.get(i));
                    // 渲染划分
                    int b = ~(i+1) & 0xFF;
                    RenderHelper.stencilFunc(GL11.GL_EQUAL, b, 0xFF);
                    renderTempPart(matrixStack, transformType, renderType, light, overlay, divisionNodePaths.get(i));
                }
            }
        }
    }
    float getScopeApertureRadius() {
        return getScopeApertureRadius(getClientAimingProgress());
    }
    float getScopeApertureRadius(float aimingProgress) {
        // 80 follows the original 1.20.1 aperture size; ADS progress only scales the aperture.
        float radius = 80 * scopeViewRadiusModifier;
        return radius * aimingProgress;
    }
    float getClientAimingProgress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            return IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partialTick);
        }
        return 0.0F;
    }
    private void renderBoth(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
		// 当加速渲染加载则使用加速渲染提供的方法进行加速
		if (ARCompat.shouldAccelerate()) {
			renderBothAccelerated(matrixStack, transformType, renderType, light, overlay);
			return;
		}

        RenderHelper.enableItemEntityStencilTest();
        try {
            // 清空模板缓冲区、准备绘制模板缓冲
            RenderHelper.clearStencilBuffer();
            if (ocularRingPath != null) {
                RenderHelper.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                // 渲染目镜外环
                renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
            }
            // 渲染目镜以写入模板桓冲值 (暂时只渲染 ocular_scope)
            renderOcularStencil(matrixStack, transformType, renderType, light, overlay, true);
            // 渲染镜身
            if (scopeBodyPath != null) {
                RenderHelper.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
                renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
            }
            // 渲染目镜以写入模板桓冲值 (渲染其他的目镜)
            renderOcularStencil(matrixStack, transformType, renderType, light, overlay, false);
            // 渲染目镜遮罩和划分
            renderOcularAndDivision(matrixStack, transformType, renderType, light, overlay, true);
        } finally {
            RenderHelper.disableItemEntityStencilTest();
        }
        // 渲染其他部分
        super.render(matrixStack, transformType, renderType, light, overlay);
    }
    private void renderSight(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
		if (ARCompat.shouldAccelerate()) {
			renderSightAccelerated(matrixStack, transformType, renderType, light, overlay);
			return;
		}

        RenderHelper.enableItemEntityStencilTest();
        try {
            // 清空模板缓冲区、准备绘制模板缓冲
            RenderHelper.clearStencilBuffer();
            // 渲染目镜以写入模板桓冲值
            renderOcularStencil(matrixStack, transformType, renderType, light, overlay, false);
            // 渲染划分
            renderDivisionOnly(matrixStack, transformType, renderType, light, overlay);
        } finally {
            RenderHelper.disableItemEntityStencilTest();
        }
        // 渲染其他部分
        if (scopeBodyPath != null) {
            renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
        }
        super.render(matrixStack, transformType, renderType, light, overlay);
    }
    private void renderScope(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
		if (ARCompat.shouldAccelerate()) {
			renderScopeAccelerated(matrixStack, transformType, renderType, light, overlay);
			return;
		}

        RenderHelper.enableItemEntityStencilTest();
        try {
            // 清空模板缓冲区、准备绘制模板缓冲
            RenderHelper.clearStencilBuffer();
            // 渲染目镜外环
            if (ocularRingPath != null) {
                RenderHelper.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
            }
            // 渲染目镜以写入模板桓冲值
            renderOcularStencil(matrixStack, transformType, renderType, light, overlay, false);
            // 渲染镜身
            if (scopeBodyPath != null) {
                RenderHelper.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
                renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
            }
            // 渲染目镜遮罩和划分
            renderOcularAndDivision(matrixStack, transformType, renderType, light, overlay, false);
        } finally {
            RenderHelper.disableItemEntityStencilTest();
        }
        // 渲染其他部分
        super.render(matrixStack, transformType, renderType, light, overlay);
    }

	public void renderBothAccelerated(
			PoseStack matrixStack,
			ItemDisplayContext transformType,
			RenderType renderType,
			int light,
			int overlay
	) {
		// 缓存PoseStack防止之后坐标变换出现问题
		var poseStack = new PoseStack();
		poseStack.last().pose().set(matrixStack.last().pose());
		poseStack.last().normal().set(matrixStack.last().normal());

		// 设置外环的渲染层和渲染前后任务
		// 清空模板缓冲区、准备绘制模板缓冲
		ARCompat.setRenderLayer(-943);
		ARCompat.setRenderBeforeFunction(() -> {
			// 清除上模板残留, 这里其实可以按原始渲染方法移出去, 但为了统一先放入第一层渲染
			RenderHelper.enableItemEntityStencilTest();
			RenderHelper.clearStencilBuffer();

			// 渲染目镜外环所需的模板函数
			RenderHelper.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
			RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
		});

		// 画完目镜外环后, 临时关闭模板缓冲区防止渲染异常
		ARCompat.setRenderAfterFunction(RenderHelper::disableItemEntityStencilTest);

		try {
			if (ocularRingPath != null) {
				// 渲染目镜外环
				renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
			}
		} finally {
			// 重置外环层, 还原现场, 进行下一步绘制
			ARCompat.resetRenderLayer();
			ARCompat.resetRenderBeforeFunction();
			ARCompat.resetRenderAfterFunction();
		}

		// 设置镜身的渲染层和渲染前后任务
		ARCompat.setRenderLayer(-943 + 1);
		ARCompat.setRenderBeforeFunction(() -> {
			// 重新启用上一层关闭的模板缓冲区
			RenderHelper.enableItemEntityStencilTest();

			// 我们在层中进行渲染, 需要关闭加速, 否则这里的渲染会被导向到加速管线中, 造成被延后和内存泄漏
			ARCompat.disableAcceleration();

			// 在层间操作已经绑定了VAO, Minecraft的标准绘制方法会改变全局VAO状态, 因此需要缓存VAO
			int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

			try {
				// 渲染目镜以写入模板桓冲值 (暂时只渲染 ocular_scope)
				renderOcularStencil(poseStack, transformType, renderType, light, overlay, true);
			} finally {
				// 重新绑回VAO并重新开启加速
				GL30.glBindVertexArray(vao);
				ARCompat.resetAcceleration();
			}

			// 设置镜身需要的模板函数
			RenderHelper.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
		});

		// 画完镜身后, 渲染其他目镜及遮罩并关闭模板缓冲
		ARCompat.setRenderAfterFunction(() -> {
			// 我们在层中进行渲染, 需要关闭加速, 否则这里的渲染会被导向到加速管线中, 造成被延后和内存泄漏
			ARCompat.disableAcceleration();

			// 在层间操作已经绑定了VAO, Minecraft的标准绘制方法会改变全局VAO状态, 因此需要缓存VAO
			int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

			try {
				// 渲染目镜以写入模板桓冲值 (渲染其他的目镜)
				renderOcularStencil(poseStack, transformType, renderType, light, overlay, false);
				// 渲染目镜遮罩和划分
				renderOcularAndDivision(poseStack, transformType, renderType, light, overlay, true);
			} finally {
				// 重新绑回VAO、重新开启加速并关闭模板缓冲
				GL30.glBindVertexArray(vao);
				ARCompat.resetAcceleration();
				RenderHelper.disableItemEntityStencilTest();
			}
		});

		try {
			if (scopeBodyPath != null) {
				// 渲染镜身
				renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
			}
		} finally {
			// 重置镜身层, 还原现场, 进行最后一步其他部分绘制
			ARCompat.resetRenderLayer();
			ARCompat.resetRenderBeforeFunction();
			ARCompat.resetRenderAfterFunction();
		}

		// 设置其他的渲染层, 保证其在最后一部分渲染
		ARCompat.setRenderLayer(-943 + 2);

		try {
			// 渲染其他部分
			super.render(matrixStack, transformType, renderType, light, overlay);
		} finally {
			// 重置层, 还原现场, 完成配件渲染
			ARCompat.resetRenderLayer();
		}
	}

	private void renderSightAccelerated(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
		// 缓存PoseStack防止之后坐标变换出现问题
		var poseStack = new PoseStack();
		poseStack.last().pose().set(matrixStack.last().pose());
		poseStack.last().normal().set(matrixStack.last().normal());

		// 设置层前任务, 清除模板缓冲并绘制目镜和划分
		ARCompat.setRenderLayer(-943);
		ARCompat.setRenderBeforeFunction(() -> {
			// 清除上模板残留, 这里其实可以按原始渲染方法移出去, 但为了统一先放入第一层渲染
			RenderHelper.enableItemEntityStencilTest();
			RenderHelper.clearStencilBuffer();

			// 我们在层中进行渲染, 需要关闭加速, 否则这里的渲染会被导向到加速管线中, 造成被延后和内存泄漏
			ARCompat.disableAcceleration();

			// 在层间操作已经绑定了VAO, Minecraft的标准绘制方法会改变全局VAO状态, 因此需要缓存VAO
			int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

			try {
				// 渲染目镜以写入模板桓冲值
				renderOcularStencil(poseStack, transformType, renderType, light, overlay, false);
				// 渲染划分
				renderDivisionOnly(poseStack, transformType, renderType, light, overlay);
			} finally {
				// 重新绑回VAO、重新开启加速并关闭模板缓冲
				GL30.glBindVertexArray(vao);
				ARCompat.resetAcceleration();
				RenderHelper.disableItemEntityStencilTest();
			}
		});

		try {
			// 渲染其他部分
			if (scopeBodyPath != null) {
				renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
			}
			super.render(matrixStack, transformType, renderType, light, overlay);
		} finally {
			// 重置层, 还原现场, 完成渲染
			ARCompat.resetRenderLayer();
			ARCompat.resetRenderBeforeFunction();
		}
	}

	private void renderScopeAccelerated(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
		// 缓存PoseStack防止之后坐标变换出现问题
		var poseStack = new PoseStack();
		poseStack.last().pose().set(matrixStack.last().pose());
		poseStack.last().normal().set(matrixStack.last().normal());

		// 设置外环的渲染层和渲染前后任务
		// 清空模板缓冲区、准备绘制模板缓冲
		ARCompat.setRenderLayer(-943);
		ARCompat.setRenderBeforeFunction(() -> {
			// 清除上模板残留, 这里其实可以按原始渲染方法移出去, 但为了统一先放入第一层渲染
			RenderHelper.enableItemEntityStencilTest();
			RenderHelper.clearStencilBuffer();

			// 渲染目镜外环所需的模板函数
			RenderHelper.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
			RenderHelper.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
		});

		// 画完目镜外环后, 临时关闭模板缓冲区防止渲染异常
		ARCompat.setRenderAfterFunction(RenderHelper::disableItemEntityStencilTest);

		try {
			// 渲染目镜外环
			if (ocularRingPath != null) {
				renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
			}
		} finally {
			// 重置外环层, 还原现场, 进行下一步绘制
			ARCompat.resetRenderLayer();
			ARCompat.resetRenderBeforeFunction();
			ARCompat.resetRenderAfterFunction();
		}

		// 设置镜身的渲染层和渲染前后任务
		ARCompat.setRenderLayer(-943 + 1);
		ARCompat.setRenderBeforeFunction(() -> {
			// 重新启用上一层关闭的模板缓冲区
			RenderHelper.enableItemEntityStencilTest();

			// 我们在层中进行渲染, 需要关闭加速, 否则这里的渲染会被导向到加速管线中, 造成被延后和内存泄漏
			ARCompat.disableAcceleration();

			// 在层间操作已经绑定了VAO, Minecraft的标准绘制方法会改变全局VAO状态, 因此需要缓存VAO
			int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

			try {
				// 渲染目镜以写入模板桓冲值
				renderOcularStencil(poseStack, transformType, renderType, light, overlay, false);
			} finally {
				// 重新绑回VAO并重新开启加速
				GL30.glBindVertexArray(vao);
				ARCompat.resetAcceleration();
			}

			// 设置镜身需要的模板函数
			RenderHelper.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
		});

		// 画完镜身后, 渲染其他目镜及遮罩并关闭模板缓冲
		ARCompat.setRenderAfterFunction(() -> {
			// 我们在层中进行渲染, 需要关闭加速, 否则这里的渲染会被导向到加速管线中, 造成被延后和内存泄漏
			ARCompat.disableAcceleration();

			// 在层间操作已经绑定了VAO, Minecraft的标准绘制方法会改变全局VAO状态, 因此需要缓存VAO
			int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

			try {
				// 渲染目镜遮罩和划分
				renderOcularAndDivision(poseStack, transformType, renderType, light, overlay, false);
			} finally {
				// 重新绑回VAO、重新开启加速并关闭模板缓冲
				GL30.glBindVertexArray(vao);
				ARCompat.resetAcceleration();
				RenderHelper.disableItemEntityStencilTest();
			}
		});

		try {
			// 渲染镜身
			if (scopeBodyPath != null) {
				renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
			}
		} finally {
			// 重置镜身层, 还原现场, 进行最后一步其他部分绘制
			ARCompat.resetRenderLayer();
			ARCompat.resetRenderBeforeFunction();
			ARCompat.resetRenderAfterFunction();
		}

		// 设置其他的渲染层, 保证其在最后一部分渲染
		ARCompat.setRenderLayer(-943 + 2);

		try {
			// 渲染其他部分
			super.render(matrixStack, transformType, renderType, light, overlay);
		} finally {
			// 重置层, 还原现场, 完成配件渲染
			ARCompat.resetRenderLayer();
		}
	}

    private static class OcularWrapper{
        public ModelRendererWrapper renderer;
        public boolean isScope;

        public OcularWrapper (ModelRendererWrapper renderer, boolean isScope){
            this.renderer = renderer;
            this.isScope = isScope;
        }
    }
}
