package com.phasetranscrystal.fpsmatch.common.client.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

public record RenderableArea(String key, Component name, int color, AreaData area) {
    public void render(PoseStack poseStack, MultiBufferSource bufferSource) {
        area.renderArea(poseStack, bufferSource, color);
    }
}
