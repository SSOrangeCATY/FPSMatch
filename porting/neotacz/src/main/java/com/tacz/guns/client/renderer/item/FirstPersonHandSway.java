package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

public final class FirstPersonHandSway {
    private static final float VANILLA_HAND_DELAY_SCALE = -0.1F;
    private static final float MODEL_OFFSET_SCALE = 0.1F / 16F / 3F;
    private static final float MODEL_ROTATION_SCALE = 0.05F;

    private final float cancelX;
    private final float cancelY;
    private final float modelX;
    private final float modelY;

    private FirstPersonHandSway(float cancelX, float cancelY, float modelX, float modelY) {
        this.cancelX = cancelX;
        this.cancelY = cancelY;
        this.modelX = modelX;
        this.modelY = modelY;
    }

    static FirstPersonHandSway capture(LocalPlayer player, float partialTick) {
        float xBob = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
        float cancelX = player.getViewXRot(partialTick) - xBob;
        float cancelY = player.getViewYRot(partialTick) - yBob;
        float modelX = limitModelSway(Mth.wrapDegrees(cancelX));
        float modelY = limitModelSway(Mth.wrapDegrees(cancelY));
        return new FirstPersonHandSway(cancelX, cancelY, modelX, modelY);
    }

    void cancelVanillaHandDelay(PoseStack poseStack) {
        poseStack.mulPose(Axis.XP.rotationDegrees(cancelX * VANILLA_HAND_DELAY_SCALE));
        poseStack.mulPose(Axis.YP.rotationDegrees(cancelY * VANILLA_HAND_DELAY_SCALE));
    }

    void applyTo(@Nullable BedrockPart rootNode) {
        if (rootNode == null) {
            return;
        }
        rootNode.offsetX += modelY * MODEL_OFFSET_SCALE;
        rootNode.offsetY += -modelX * MODEL_OFFSET_SCALE;
        rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(modelX * MODEL_ROTATION_SCALE));
        rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(modelY * MODEL_ROTATION_SCALE));
    }

    public void withTemporaryModelSway(@Nullable BedrockPart rootNode, Runnable action) {
        if (rootNode == null) {
            action.run();
            return;
        }
        float oldOffsetX = rootNode.offsetX;
        float oldOffsetY = rootNode.offsetY;
        float oldOffsetZ = rootNode.offsetZ;
        Quaternionf oldQuaternion = new Quaternionf(rootNode.additionalQuaternion);
        applyTo(rootNode);
        try {
            action.run();
        } finally {
            rootNode.offsetX = oldOffsetX;
            rootNode.offsetY = oldOffsetY;
            rootNode.offsetZ = oldOffsetZ;
            rootNode.additionalQuaternion.set(oldQuaternion);
        }
    }

    private static float limitModelSway(float value) {
        return (float) Math.tanh(value / 25F) * 25F;
    }
}
