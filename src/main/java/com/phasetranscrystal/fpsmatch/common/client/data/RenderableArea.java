package com.phasetranscrystal.fpsmatch.common.client.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.shapes.Shapes;

public record RenderableArea(String key, Component name, int color, AreaData area) {
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, float lineWidth) {
        submitNodeCollector.submitShapeOutline(poseStack, Shapes.create(area.aabb()), RenderTypes.lines(), color, lineWidth, false);
    }
}
