package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import net.minecraft.world.item.ItemDisplayContext;

public class RightHandRender implements IFunctionalRenderer {
    public RightHandRender(BedrockAnimatedModel bedrockGunModel) {
    }

    @Override
    public boolean usesRetainedSubmitPrepass() {
        return true;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        if (transformType.firstPerson()) {
            // 第一人称手臂由 GunItemRendererWrapper 的顶层 retained 提交统一处理。
            return;
        }
    }
}
