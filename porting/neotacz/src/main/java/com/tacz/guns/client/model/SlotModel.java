package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.FaceUVsItem;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;


public final class SlotModel {
    private final BedrockPart bone;

    public SlotModel(boolean illuminated) {
        bone = new BedrockPart("slot");
        bone.setPos(8.0F, 24.0F, -10.0F);
        bone.cubes.add(new BedrockCubePerFace(-16.0F, -16.0F, 9.5F, 16.0F, 16.0F, 0, 0, 16, 16, FaceUVsItem.singleSouthFace()));
        bone.illuminated = illuminated;
    }

    public SlotModel() {
        this(false);
    }

    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        bone.render(poseStack, ItemDisplayContext.GUI, buffer, packedLight, packedOverlay);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int packedLight, int packedOverlay) {
        RenderHelper.submitCustomGeometry(collector, poseStack, renderType, (pose, buffer) -> {
            PoseStack callbackPoseStack = new PoseStack();
            callbackPoseStack.last().pose().set(pose.pose());
            callbackPoseStack.last().normal().set(pose.normal());
            this.renderToBuffer(callbackPoseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        });
    }
}
