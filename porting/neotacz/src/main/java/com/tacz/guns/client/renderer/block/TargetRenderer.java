package com.tacz.guns.client.renderer.block;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import java.util.Optional;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class TargetRenderer implements BlockEntityRenderer<TargetBlockEntity, TargetRenderer.TargetRenderState> {
    private static final String UPPER_NAME = "target_upper";
    private static final String HEAD_NAME = "head";

    private final PlayerSkinRenderCache playerSkinRenderCache;

    public TargetRenderer(BlockEntityRendererProvider.Context context) {
        this.playerSkinRenderCache = context.playerSkinRenderCache();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MODEL_LOCATION);
    }

    @Override
    public TargetRenderState createRenderState() {
        return new TargetRenderState();
    }

    @Override
    public void extractRenderState(TargetBlockEntity blockEntity, TargetRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(TargetBlock.FACING);
        state.rotation = -Mth.lerp(partialTicks, blockEntity.oRot, blockEntity.rot);
        state.owner = blockEntity.getOwner();
    }

    @Override
    public void submit(TargetRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        getModel().ifPresent(model -> {
            BedrockPart headModel = model.getNode(HEAD_NAME);
            BedrockPart upperModel = model.getNode(UPPER_NAME);
            if (headModel == null || upperModel == null) {
                return;
            }

            poseStack.pushPose();
            poseStack.translate(0.5, 0.225, 0.5);
            poseStack.mulPose(Axis.YN.rotationDegrees(state.facing.get2DDataValue() * 90));
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            poseStack.translate(0, -1.275, 0.0125);
            RenderType renderType = RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_TEXTURE_LOCATION);
            submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                PoseStack callbackPoseStack = BedrockSubmitUtils.fromPose(pose);
                boolean headVisible = headModel.visible;
                float upperXRot = upperModel.xRot;
                try {
                    upperModel.xRot = (float) Math.toRadians(state.rotation);
                    headModel.visible = false;
                    model.renderToBuffer(callbackPoseStack, ItemDisplayContext.NONE, buffer, state.lightCoords, OverlayTexture.NO_OVERLAY);
                } finally {
                    upperModel.xRot = upperXRot;
                    headModel.visible = headVisible;
                }
            });

            if (state.owner != null) {
                RenderType skullRenderType = this.playerSkinRenderCache
                        .getOrDefault(BedrockSubmitUtils.toResolvableProfile(state.owner))
                        .renderType();
                poseStack.translate(0, 1.25, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(state.rotation));
                submitNodeCollector.submitCustomGeometry(poseStack, skullRenderType, (pose, buffer) -> {
                    PoseStack callbackPoseStack = BedrockSubmitUtils.fromPose(pose);
                    boolean visible = headModel.visible;
                    try {
                        headModel.visible = true;
                        headModel.render(callbackPoseStack, ItemDisplayContext.NONE, buffer, state.lightCoords, OverlayTexture.NO_OVERLAY);
                    } finally {
                        headModel.visible = visible;
                    }
                });
            }
            poseStack.popPose();
        });
    }

    @Override
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    public static class TargetRenderState extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float rotation;
        @Nullable GameProfile owner;
    }
}
