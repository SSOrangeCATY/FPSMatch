package com.phasetranscrystal.fpsmatch.common.client.data;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public record RenderablePoint(String key, Component name, int color, Vec3 position) {
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, float lineWidth) {
        AABB markerBox = new AABB(
                position.x - 0.18D, position.y - 0.18D, position.z - 0.18D,
                position.x + 0.18D, position.y + 0.18D, position.z + 0.18D
        );
        AABB verticalLine = new AABB(
                position.x - 0.02D, position.y, position.z - 0.02D,
                position.x + 0.02D, position.y + 1.1D, position.z + 0.02D
        );
        submitNodeCollector.submitShapeOutline(poseStack, Shapes.create(markerBox), RenderTypes.lines(), color, lineWidth, false);
        submitNodeCollector.submitShapeOutline(poseStack, Shapes.create(verticalLine), RenderTypes.lines(), color, lineWidth, false);
    }
}
