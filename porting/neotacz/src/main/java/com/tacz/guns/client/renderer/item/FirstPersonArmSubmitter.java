package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.client.ClientHooks;

import javax.annotation.Nullable;
import java.util.List;

public final class FirstPersonArmSubmitter {
    private FirstPersonArmSubmitter() {
    }

    public static void submitGunHands(AbstractClientPlayer player, BedrockGunModel gunModel, PoseStack gunPose,
                                      SubmitNodeCollector submitNodeCollector, int light) {
        if (player == null || submitNodeCollector == null) {
            return;
        }
        submitGunHand(player, gunModel.getLeftHandPosPath(), HumanoidArm.LEFT, gunPose, submitNodeCollector, light);
        submitGunHand(player, gunModel.getRightHandPosPath(), HumanoidArm.RIGHT, gunPose, submitNodeCollector, light);
    }

    private static void submitGunHand(AbstractClientPlayer player, @Nullable List<BedrockPart> anchorPath, HumanoidArm arm,
                                      PoseStack gunPose, SubmitNodeCollector submitNodeCollector, int light) {
        if (anchorPath == null || anchorPath.isEmpty()) {
            return;
        }
        PoseStack handPose = copyPoseStack(gunPose);
        for (BedrockPart bedrockPart : anchorPath) {
            bedrockPart.translateAndRotateAndScale(handPose);
        }
        handPose.mulPose(Axis.ZP.rotationDegrees(180f));
        if (ClientHooks.renderSpecificFirstPersonArm(handPose, submitNodeCollector, light, player, arm)) {
            return;
        }

        Identifier skinTexture = player.getSkin().body().texturePath();
        RenderHelper.submitCustomGeometry(submitNodeCollector, handPose, RenderTypes.entityTranslucent(skinTexture), (pose, buffer) -> {
            PoseStack callbackPoseStack = new PoseStack();
            callbackPoseStack.last().pose().set(pose.pose());
            callbackPoseStack.last().normal().set(pose.normal());
            renderArmModelPart(player, arm, callbackPoseStack, buffer, light);
        });
    }

    public static void renderGunHandsToBuffer(AbstractClientPlayer player, BedrockGunModel gunModel, PoseStack gunPose,
                                              VertexConsumer buffer, int light) {
        if (player == null || buffer == null) {
            return;
        }
        renderGunHandToBuffer(player, gunModel.getLeftHandPosPath(), HumanoidArm.LEFT, gunPose, buffer, light);
        renderGunHandToBuffer(player, gunModel.getRightHandPosPath(), HumanoidArm.RIGHT, gunPose, buffer, light);
    }

    private static void renderGunHandToBuffer(AbstractClientPlayer player, @Nullable List<BedrockPart> anchorPath,
                                              HumanoidArm arm, PoseStack gunPose, VertexConsumer buffer, int light) {
        if (anchorPath == null || anchorPath.isEmpty()) {
            return;
        }
        PoseStack handPose = copyPoseStack(gunPose);
        for (BedrockPart bedrockPart : anchorPath) {
            bedrockPart.translateAndRotateAndScale(handPose);
        }
        handPose.mulPose(Axis.ZP.rotationDegrees(180f));
        renderArmModelPart(player, arm, handPose, buffer, light);
    }

    private static void renderArmModelPart(AbstractClientPlayer player, HumanoidArm arm, PoseStack poseStack,
                                           VertexConsumer buffer, int light) {
        AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        PlayerModel model = renderer.getModel();
        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        boolean sleeveVisible = player.isModelPartShown(arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);

        PartPose leftArmPose = model.leftArm.storePose();
        PartPose rightArmPose = model.rightArm.storePose();
        float leftArmXScale = model.leftArm.xScale;
        float leftArmYScale = model.leftArm.yScale;
        float leftArmZScale = model.leftArm.zScale;
        float rightArmXScale = model.rightArm.xScale;
        float rightArmYScale = model.rightArm.yScale;
        float rightArmZScale = model.rightArm.zScale;
        boolean leftArmVisible = model.leftArm.visible;
        boolean rightArmVisible = model.rightArm.visible;
        boolean leftSleeveVisible = model.leftSleeve.visible;
        boolean rightSleeveVisible = model.rightSleeve.visible;

        boolean accelerated = ARCompat.shouldAccelerate();
        if (accelerated) {
            ARCompat.setRenderingLevel();
        }
        try {
            armPart.resetPose();
            armPart.visible = true;
            sleevePart.visible = sleeveVisible;
            model.leftArm.zRot = -0.1F;
            model.rightArm.zRot = 0.1F;
            armPart.render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        } finally {
            model.leftArm.loadPose(leftArmPose);
            model.rightArm.loadPose(rightArmPose);
            model.leftArm.xScale = leftArmXScale;
            model.leftArm.yScale = leftArmYScale;
            model.leftArm.zScale = leftArmZScale;
            model.rightArm.xScale = rightArmXScale;
            model.rightArm.yScale = rightArmYScale;
            model.rightArm.zScale = rightArmZScale;
            model.leftArm.visible = leftArmVisible;
            model.rightArm.visible = rightArmVisible;
            model.leftSleeve.visible = leftSleeveVisible;
            model.rightSleeve.visible = rightSleeveVisible;
            if (accelerated) {
                ARCompat.resetRenderingLevel();
            }
        }
    }

    private static PoseStack copyPoseStack(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }
}
