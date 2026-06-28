package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.debug.ScopeRenderDebug;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.renderer.entity.BulletTracerDebug;
import com.tacz.guns.client.renderer.entity.BulletTracerRenderSpace;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.entity.FirstPersonTracerAnchor;
import com.tacz.guns.util.RenderDistance;
import com.tacz.guns.util.RenderHelper;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.minecraft.world.item.ItemDisplayContext.*;

/**
 * 负责主要的枪械动画模型渲染。额外的效果见 {@link com.tacz.guns.client.event.FirstPersonRenderGunEvent}
 */
public class GunItemRendererWrapper extends AnimateGeoItemRenderer<BedrockGunModel, GunAnimationStateContext> {
    private static final SlotModel SLOT_GUN_MODEL = new SlotModel();
    private static final int FIRST_PERSON_GUN_BODY_ORDER = -860_000;
    private static BedrockGunModel lastModel = null;
    public static final Vector3f muzzleRenderOffset = new Vector3f();
    private static boolean muzzleRenderOffsetValid = false;
    private static int muzzleRenderPlayerId = -1;
    @Nullable
    private static Identifier muzzleRenderGunId = null;
    @Nullable
    private static Identifier muzzleRenderDisplayId = null;
    @Nullable
    private static FirstPersonTracerAnchor firstPersonTracerAnchor = null;
    private static long firstPersonTracerAnchorSequence = 0;

    public GunItemRendererWrapper() {
        super();
    }

    public static Optional<Vector3f> copyMuzzleRenderOffset(@Nullable Identifier gunId, @Nullable Identifier displayId, int playerId) {
        if (!isMatchingMuzzleSnapshot(gunId, displayId, playerId)) {
            return Optional.empty();
        }
        return Optional.of(new Vector3f(muzzleRenderOffset));
    }

    public static Optional<FirstPersonTracerAnchor> copyFirstPersonTracerAnchor(@Nullable Identifier gunId, @Nullable Identifier displayId, int playerId) {
        if (!isMatchingMuzzleSnapshot(gunId, displayId, playerId)
                || firstPersonTracerAnchor == null
                || !firstPersonTracerAnchor.matches(gunId, displayId, playerId)) {
            return Optional.empty();
        }
        return Optional.of(firstPersonTracerAnchor);
    }

    public static void refreshFirstPersonTracerAnchorForShot(@Nullable Identifier gunId, @Nullable Identifier displayId, int playerId) {
        if (gunId == null || displayId == null || !isMatchingMuzzleSnapshot(gunId, displayId, playerId)) {
            firstPersonTracerAnchor = null;
            return;
        }
        firstPersonTracerAnchor = captureFirstPersonTracerAnchor(playerId, gunId, displayId, muzzleRenderOffset);
        if (firstPersonTracerAnchor == null || !firstPersonTracerAnchor.isFinite()) {
            firstPersonTracerAnchor = null;
            return;
        }
        BulletTracerDebug.muzzleSnapshot(firstPersonTracerAnchor, "shot_refresh");
    }

    private static void clearMuzzleRenderOffset() {
        muzzleRenderOffset.zero();
        muzzleRenderOffsetValid = false;
        muzzleRenderPlayerId = -1;
        muzzleRenderGunId = null;
        muzzleRenderDisplayId = null;
        firstPersonTracerAnchor = null;
    }

    private static boolean isMatchingMuzzleSnapshot(@Nullable Identifier gunId, @Nullable Identifier displayId, int playerId) {
        return muzzleRenderOffsetValid && muzzleRenderPlayerId == playerId
                && Objects.equals(muzzleRenderGunId, gunId) && Objects.equals(muzzleRenderDisplayId, displayId)
                && isFiniteFirstPersonOffset(muzzleRenderOffset);
    }

    private static boolean isFiniteFirstPersonOffset(Vector3f offset) {
        return Float.isFinite(offset.x) && Float.isFinite(offset.y) && Float.isFinite(offset.z);
    }

    @Override
    public GunAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
        GunAnimationStateContext context = new GunAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(GunAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
        context.setPartialTicks(partialTick);
        context.setCurrentGunItem(stack);
    }

    @Override
    public void tryInit(ItemStack stack, Player player, float partialTick) {
        super.tryInit(stack, player, partialTick);
    }

    @Override
    public void tryExit(ItemStack stack, long putAwayTime) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.processContextIfExist(context -> {
            context.setPutAwayTime(putAwayTime / 1000F);
            context.setCurrentGunItem(stack);
        });
        if(stateMachine.isInitialized()) {
            stateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY);
//            KeepingItemRenderer.getRenderer().keep(stack, putAwayTime);
            stateMachine.exit();
            stateMachine.setExitingTime(putAwayTime + 50);
        }
    }

    @Override
    public long getPutAwayTime(ItemStack stack) {
        if (stack.getItem() instanceof IGun iGun) {
            return TimelessAPI.getCommonGunIndex(iGun.getGunId(stack))
                    .map(index -> (long) (index.getGunData().getPutAwayTime() * 1000L))
                    .orElse(0L);
        }
        return 0;
    }

    @Nullable
    @Override
    public LuaAnimationStateMachine<GunAnimationStateContext> getStateMachine(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getAnimationStateMachine).orElse(null);
    }

    @Override
    public BedrockGunModel getModel(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getGunModel).orElse(null);
    }

    @Override
    public Identifier getTextureLocation(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getModelTexture).orElse(null);
    }

    @Override
    public void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, LocalPlayer player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Optional.ofNullable(getModel(stack)).ifPresent(model -> {
            if (lastModel != model) {
                // 切换枪械模型的时候清理一下摄像机动画数据，以避免上一次播放到一半的摄像机动画影响观感。
                model.cleanCameraAnimationTransform();
                lastModel = model;
            }
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(stack);
            float multiplier = 1 - aimingProgress + aimingProgress / (float) Math.sqrt(zoom);
            this.applyLevelCameraAnimation(event, stack, multiplier);
        });
    }

    @Override
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, LocalPlayer player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Optional.ofNullable(getModel(stack)).ifPresent(model -> {
            PoseStack poseStack = event.getPoseStack();
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(stack);
            float multiplier = 1 - aimingProgress + aimingProgress / (float) Math.sqrt(zoom);
            Quaternionf quaternion = MathUtil.multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
            poseStack.mulPose(quaternion);
            // 截至目前，摄像机动画数据已消费完毕。是否有更好的清理动画数据的方法？
            model.cleanCameraAnimationTransform();
        });
    }

    @Override
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                  int light, float partialTick) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            clearMuzzleRenderOffset();
            return;
        }
        ScopeRenderDebug.path("gun_render_first_person", null, stack, ctx, "");

        TimelessAPI.getGunDisplay(stack).ifPresentOrElse(display -> {
            BedrockGunModel gunModel = display.getGunModel();
            var animationStateMachine = display.getAnimationStateMachine();
            if (gunModel == null) {
                clearMuzzleRenderOffset();
                return;
            }

            // 在渲染之前，先更新动画，让动画数据写入模型
            if (animationStateMachine != null) {
                animationStateMachine.processContextIfExist(context -> {
                    updateContext(context, stack, player, partialTick);
                });
                animationStateMachine.update();
            }

            poseStack.pushPose();
            FirstPersonHandSway handSway = FirstPersonHandSway.capture(player, partialTick);
            handSway.cancelVanillaHandDelay(poseStack);
            BedrockPart rootNode = gunModel.getRootNode();
            // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
            poseStack.translate(0, 1.5f, 0);
            // 基岩版模型是上下颠倒的，需要翻转过来。
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            // 应用持枪姿态变换，如第一人称摄像机定位
            FirstPersonRenderGunEvent.applyFirstPersonGunTransform(player, stack, poseStack, gunModel, partialTick);

            // 如果正在打开改装界面，则取消手臂渲染
            boolean renderHand = gunModel.getRenderHand();
            boolean renderHandForCallback = renderHand;
            if (RefitTransform.getOpeningProgress() != 0) {
                renderHandForCallback = false;
            }
            // 调用枪械模型渲染
            RenderType baseRenderType = display.enablesTransparency()
                    ? RenderTypes.entityTranslucent(display.getModelTexture())
                    : RenderTypes.entityCutout(display.getModelTexture());
            boolean finalRenderHandForCallback = renderHandForCallback;
            boolean previousRenderHand = gunModel.getRenderHand();
            MuzzleFlashRender.isSelf = true;
            ShellRender.isSelf = true;
            gunModel.setRenderHand(finalRenderHandForCallback);
            try {
                handSway.withTemporaryModelSway(rootNode, () -> RenderHelper.withSubmitNodeCollector(submitNodeCollector, () ->
                        RenderHelper.withDeferredFunctionalRendererCollection(() ->
                                gunModel.collectDeferredFunctionalRenderers(poseStack, stack, ctx, light, OverlayTexture.NO_OVERLAY))));
            } finally {
                gunModel.setRenderHand(previousRenderHand);
                MuzzleFlashRender.isSelf = false;
                ShellRender.isSelf = false;
            }
            boolean scopeStencilPassSubmitted = gunModel.submitFirstPersonScopeStencilPass(
                    submitNodeCollector,
                    poseStack,
                    player,
                    stack,
                    ctx,
                    baseRenderType,
                    display.getModelTexture(),
                    light,
                    OverlayTexture.NO_OVERLAY,
                    FIRST_PERSON_GUN_BODY_ORDER,
                    handSway,
                    finalRenderHandForCallback);
            if (!scopeStencilPassSubmitted) {
                submitFirstPersonGunBodyGeometry(submitNodeCollector, poseStack, baseRenderType, (callbackPoseStack, buffer) -> {
                    boolean callbackPreviousRenderHand = gunModel.getRenderHand();
                    gunModel.setRenderHand(finalRenderHandForCallback);
                    try {
                        handSway.applyTo(rootNode);
                        RenderHelper.withDeferredRenderersSuppressed(() ->
                                RenderHelper.withSubmitNodeCollector(submitNodeCollector, () ->
                                        gunModel.renderToBuffer(callbackPoseStack, stack, ctx, buffer, light, OverlayTexture.NO_OVERLAY)));
                    } finally {
                        gunModel.setRenderHand(callbackPreviousRenderHand);
                        gunModel.cleanAnimationTransform();
                    }
                });
            }
            if (!scopeStencilPassSubmitted && finalRenderHandForCallback) {
                handSway.withTemporaryModelSway(rootNode, () ->
                        FirstPersonArmSubmitter.submitGunHands(player, gunModel, poseStack, submitNodeCollector, light));
            }
            // 缓存枪口位置，为第一人称曳光弹渲染作准备
            handSway.withTemporaryModelSway(rootNode, () ->
                    cacheMuzzlePosition(poseStack, gunModel, player.getId(), iGun.getGunId(stack), iGun.getGunDisplayId(stack)));
            poseStack.popPose();
        }, GunItemRendererWrapper::clearMuzzleRenderOffset);
    }

    private static void submitFirstPersonGunBodyGeometry(SubmitNodeCollector submitNodeCollector, PoseStack poseStack,
                                                         RenderType renderType, GeometryRenderer renderer) {
        submitNodeCollector.order(FIRST_PERSON_GUN_BODY_ORDER).submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                RenderHelper.withSubmitCustomGeometryContext(submitNodeCollector, () -> {
                    PoseStack callbackPoseStack = new PoseStack();
                    callbackPoseStack.last().pose().set(pose.pose());
                    callbackPoseStack.last().normal().set(pose.normal());
                    renderer.render(callbackPoseStack, buffer);
                }));
    }

    private static void cacheMuzzlePosition(PoseStack poseStack, BedrockGunModel gunModel, int playerId, Identifier gunId, Identifier displayId) {
        if (gunModel.getMuzzleFlashPosPath() != null) {
            // 计算出枪口相对于摄像机中心的坐标
            poseStack.pushPose();
            for (BedrockPart bedrockPart : gunModel.getMuzzleFlashPosPath()) {
                bedrockPart.translateAndRotateAndScale(poseStack);
            }
            Matrix4f pose = poseStack.last().pose();
            double itemRenderFov = CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS.get();
            double levelRenderFov = CameraSetupEvent.WORLD_FOV_DYNAMICS.get();
            poseStack.popPose();
            // 缓存转换后的偏移坐标
            muzzleRenderOffset.set(
                    pose.m30(),
                    pose.m31(),
                    pose.m32() * Math.tan(itemRenderFov / 2 * Math.PI / 180) / Math.tan(levelRenderFov / 2 * Math.PI / 180)
            );
            muzzleRenderPlayerId = playerId;
            muzzleRenderGunId = gunId;
            muzzleRenderDisplayId = displayId;
            muzzleRenderOffsetValid = isFiniteFirstPersonOffset(muzzleRenderOffset);
            firstPersonTracerAnchor = captureFirstPersonTracerAnchor(playerId, gunId, displayId, muzzleRenderOffset);
            if (firstPersonTracerAnchor == null || !firstPersonTracerAnchor.isFinite()) {
                firstPersonTracerAnchor = null;
                muzzleRenderOffsetValid = false;
            } else {
                BulletTracerDebug.muzzleSnapshot(firstPersonTracerAnchor, "render_cache");
            }
        } else {
            clearMuzzleRenderOffset();
        }
    }

    private static @Nullable FirstPersonTracerAnchor captureFirstPersonTracerAnchor(int playerId, Identifier gunId, Identifier displayId, Vector3f localOffset) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraRight = new Vec3(camera.leftVector()).scale(-1.0);
        Vec3 cameraUp = new Vec3(camera.upVector());
        Vec3 cameraForward = new Vec3(camera.forwardVector());
        Vec3 worldOffset = BulletTracerRenderSpace.muzzleRenderOffsetToWorldOffset(localOffset);
        return new FirstPersonTracerAnchor(
                playerId,
                gunId,
                displayId,
                localOffset,
                camera.position(),
                cameraRight,
                cameraUp,
                cameraForward,
                worldOffset,
                camera.xRot(),
                camera.yRot(),
                minecraft.level.getGameTime(),
                ++firstPersonTracerAnchorSequence
        );
    }


    public void submitByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack,
                             @Nonnull SubmitNodeCollector submitNodeCollector, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }
        ScopeRenderDebug.path("gun_submit_by_item", null, stack, transformType,
                transformType.firstPerson() ? "skip_first_person" : "");
        poseStack.pushPose();
        TimelessAPI.getGunDisplay(stack).ifPresentOrElse(gunIndex -> {
            if (transformType == FIRST_PERSON_LEFT_HAND || transformType == FIRST_PERSON_RIGHT_HAND) {
                return;
            }
            if (transformType == THIRD_PERSON_LEFT_HAND) {
                return;
            }
            if (transformType == GUI) {
                submitSlotTexture(poseStack, submitNodeCollector, packedLight, packedOverlay, gunIndex.getSlotTexture());
                return;
            }

            BedrockGunModel gunModel;
            Identifier gunTexture;
            Pair<BedrockGunModel, Identifier> lodModel = gunIndex.getLodModel();
            if (lodModel == null || RenderDistance.inRenderHighPolyModelDistance(poseStack)) {
                gunModel = gunIndex.getGunModel();
                gunTexture = gunIndex.getModelTexture();
            } else {
                gunModel = lodModel.getLeft();
                gunTexture = lodModel.getRight();
            }
            if (gunModel == null) {
                submitSlotTexture(poseStack, submitNodeCollector, packedLight, packedOverlay, gunIndex.getSlotTexture());
                return;
            }

            poseStack.translate(0.5, 2, 0.5);
            poseStack.scale(-1, -1, 1);
            applyPositioningTransform(transformType, gunIndex.getTransform().getScale(), gunModel, poseStack);
            applyScaleTransform(transformType, gunIndex.getTransform().getScale(), poseStack);

            RenderType renderType = gunIndex.enablesTransparency()
                    ? RenderTypes.entityTranslucent(gunTexture)
                    : RenderTypes.entityCutout(gunTexture);
            RenderHelper.withSubmitNodeCollector(submitNodeCollector, () ->
                    RenderHelper.withDeferredFunctionalRendererCollection(() ->
                            gunModel.collectDeferredFunctionalRenderers(poseStack, stack, transformType, packedLight, packedOverlay)));
            submitModelGeometry(submitNodeCollector, poseStack, renderType, (callbackPoseStack, buffer) ->
                    RenderHelper.withDeferredRenderersSuppressed(() ->
                            RenderHelper.withSubmitNodeCollector(submitNodeCollector, () ->
                                    gunModel.renderToBuffer(callbackPoseStack, stack, transformType, buffer, packedLight, packedOverlay))));
        }, () -> submitSlotTexture(poseStack, submitNodeCollector, packedLight, packedOverlay, MissingTextureAtlasSprite.getLocation()));
        poseStack.popPose();
    }

    private static void submitSlotTexture(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, int packedOverlay, Identifier texture) {
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        RenderType renderType = RenderTypes.entityTranslucent(texture);
        submitModelGeometry(submitNodeCollector, poseStack, renderType, (callbackPoseStack, buffer) ->
                SLOT_GUN_MODEL.renderToBuffer(callbackPoseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F));
    }

    private static void applyPositioningTransform(ItemDisplayContext transformType, TransformScale scale, BedrockGunModel model,
                                                  PoseStack poseStack) {
        switch (transformType) {
            case FIXED -> applyPositioningNodeTransform(model.getFixedOriginPath(), poseStack, scale.getFixed());
            case GROUND -> applyPositioningNodeTransform(model.getGroundOriginPath(), poseStack, scale.getGround());
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> applyPositioningNodeTransform(model.getThirdPersonHandOriginPath(), poseStack, scale.getThirdPerson());
        }
    }

    private static void applyScaleTransform(ItemDisplayContext transformType, TransformScale scale, PoseStack poseStack) {
        if (scale == null) {
            return;
        }
        Vector3f vector3f = null;
        switch (transformType) {
            case FIXED -> vector3f = scale.getFixed();
            case GROUND -> vector3f = scale.getGround();
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> vector3f = scale.getThirdPerson();
        }
        if (vector3f != null) {
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(vector3f.x(), vector3f.y(), vector3f.z());
            poseStack.translate(0, -1.5, 0);
        }
    }

    private static void applyPositioningNodeTransform(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
        if (nodePath == null) {
            return;
        }
        if (scale == null) {
            scale = new Vector3f(1, 1, 1);
        }
        // 应用定位组的反向位移、旋转，使定位组的位置就是渲染中心
        poseStack.translate(0, 1.5, 0);
        for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
                poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F, -t.z * scale.z() / 16.0F);
            } else {
                poseStack.translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(), -t.z * scale.z() / 16.0F);
            }
        }
        poseStack.translate(0, -1.5, 0);
    }
}
