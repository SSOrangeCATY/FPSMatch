package com.tacz.guns.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Bridges TACZ's older VertexConsumer-based Bedrock models into Minecraft 26.1's submit pipeline.
 */
public final class BedrockSubmitUtils {
    private BedrockSubmitUtils() {
    }

    public static void submitModel(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, BedrockModel model,
                                   ItemDisplayContext transformType, int light, int overlay) {
        submitModel(collector, poseStack, renderType, model, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void submitModel(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, BedrockModel model,
                                   ItemDisplayContext transformType, int light, int overlay,
                                   float red, float green, float blue, float alpha) {
        RenderHelper.withSubmitNodeCollector(collector, () -> RenderHelper.withDeferredFunctionalRendererCollection(() ->
                model.collectDeferredFunctionalRenderers(poseStack, transformType, light, overlay)));
        RenderHelper.submitCustomGeometry(collector, poseStack, renderType, (pose, buffer) -> {
            PoseStack callbackPoseStack = fromPose(pose);
            RenderHelper.withDeferredRenderersSuppressed(() ->
                    model.renderToBuffer(callbackPoseStack, transformType, buffer, light, overlay, red, green, blue, alpha));
        });
    }

    public static void submitPart(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, BedrockPart part,
                                  ItemDisplayContext transformType, int light, int overlay) {
        submitPart(collector, poseStack, renderType, part, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void submitPart(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, BedrockPart part,
                                  ItemDisplayContext transformType, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        RenderHelper.submitCustomGeometry(collector, poseStack, renderType, (pose, buffer) -> {
            PoseStack callbackPoseStack = fromPose(pose);
            part.render(callbackPoseStack, transformType, buffer, light, overlay, red, green, blue, alpha);
        });
    }

    public static PoseStack fromPose(PoseStack.Pose pose) {
        PoseStack callbackPoseStack = new PoseStack();
        callbackPoseStack.last().pose().set(pose.pose());
        callbackPoseStack.last().normal().set(pose.normal());
        return callbackPoseStack;
    }

    public static ResolvableProfile toResolvableProfile(GameProfile profile) {
        if (profile.properties().containsKey("textures")) {
            if (profile.id() != null) {
                return ResolvableProfile.createResolved(profile);
            }
            return ResolvableProfile.createResolved(new GameProfile(Util.NIL_UUID, profile.name(), profile.properties()));
        }
        String name = profile.name();
        if (name != null && !name.isBlank()) {
            return ResolvableProfile.createUnresolved(name);
        }
        if (profile.id() != null) {
            return ResolvableProfile.createUnresolved(profile.id());
        }
        return ResolvableProfile.createUnresolved(Util.NIL_UUID);
    }
}
