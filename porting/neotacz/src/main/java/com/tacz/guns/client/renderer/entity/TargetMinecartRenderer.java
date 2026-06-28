package com.tacz.guns.client.renderer.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.entity.TargetMinecart;
import java.util.Optional;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;

public class TargetMinecartRenderer extends AbstractMinecartRenderer<TargetMinecart, TargetMinecartRenderer.TargetMinecartRenderState> {
    private static final String HEAD_NAME = "head";
    private static final String HEAD_2_NAME = "head2";

    private final PlayerSkinRenderCache playerSkinRenderCache;

    public TargetMinecartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, ModelLayers.TNT_MINECART);
        this.playerSkinRenderCache = ctx.getPlayerSkinRenderCache();
        this.shadowRadius = 0.25F;
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MINECART_MODEL_LOCATION);
    }

    @Override
    public TargetMinecartRenderState createRenderState() {
        return new TargetMinecartRenderState();
    }

    @Override
    public void extractRenderState(TargetMinecart entity, TargetMinecartRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.gameProfile = entity.getGameProfile();
    }

    @Override
    protected void submitMinecartContents(TargetMinecartRenderState state, BlockModelRenderState blockModel, PoseStack stack,
                                          SubmitNodeCollector submitNodeCollector, int lightCoords) {
        getModel().ifPresent(model -> {
            BedrockPart headModel = model.getNode(HEAD_NAME);
            BedrockPart head2Model = model.getNode(HEAD_2_NAME);
            if (headModel == null || head2Model == null) {
                return;
            }

            stack.pushPose();
            stack.translate(0.5, 1.875, 0.5);
            stack.scale(1.5f, 1.5f, 1.5f);
            stack.mulPose(Axis.ZN.rotationDegrees(180));
            stack.mulPose(Axis.YN.rotationDegrees(90));
            RenderType renderType = RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_MINECART_TEXTURE_LOCATION);
            submitNodeCollector.submitCustomGeometry(stack, renderType, (pose, buffer) -> {
                PoseStack callbackPoseStack = BedrockSubmitUtils.fromPose(pose);
                boolean headVisible = headModel.visible;
                boolean head2Visible = head2Model.visible;
                try {
                    headModel.visible = false;
                    head2Model.visible = false;
                    model.renderToBuffer(callbackPoseStack, ItemDisplayContext.NONE, buffer, lightCoords, OverlayTexture.NO_OVERLAY);
                } finally {
                    headModel.visible = headVisible;
                    head2Model.visible = head2Visible;
                }
            });

            if (state.gameProfile != null) {
                RenderType skullRenderType = this.playerSkinRenderCache
                        .getOrDefault(BedrockSubmitUtils.toResolvableProfile(state.gameProfile))
                        .renderType();
                stack.translate(0, 1, -4.5 / 16d);
                submitVisiblePart(submitNodeCollector, stack, skullRenderType, headModel, lightCoords);
                stack.translate(0, 0, 0.01);
                submitVisiblePart(submitNodeCollector, stack, skullRenderType, head2Model, lightCoords);
            }
            stack.popPose();
        });
    }

    private static void submitVisiblePart(SubmitNodeCollector submitNodeCollector, PoseStack stack, RenderType renderType,
                                          BedrockPart part, int lightCoords) {
        submitNodeCollector.submitCustomGeometry(stack, renderType, (pose, buffer) -> {
            PoseStack callbackPoseStack = BedrockSubmitUtils.fromPose(pose);
            boolean visible = part.visible;
            try {
                part.visible = true;
                part.render(callbackPoseStack, ItemDisplayContext.NONE, buffer, lightCoords, OverlayTexture.NO_OVERLAY);
            } finally {
                part.visible = visible;
            }
        });
    }

    public static class TargetMinecartRenderState extends MinecartRenderState {
        @Nullable GameProfile gameProfile;
    }
}
