package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record BombAreaData(BlockPos pos1, BlockPos pos2) {
    public boolean isPlayerInArea(Player player) {
        AABB area = new AABB(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
        return area.contains(new Vec3(player.getX(), player.getY(), player.getZ()));
    }

    public void renderArea(VertexConsumer pConsumer, PoseStack pPoseStack) {
        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        LevelRenderer.renderLineBox(pPoseStack, pConsumer,
                minX, minY, minZ, // Minimum corner
                maxX, maxY, maxZ, // Maximum corner
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F
        );
    }
}