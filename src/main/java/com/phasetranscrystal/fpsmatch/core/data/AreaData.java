package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;


public record AreaData(@Nonnull BlockPos pos1,@Nonnull BlockPos pos2) {
    public static final Codec<AreaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("Position1", BlockPos.of(0L)).forGetter(AreaData::pos1),
            BlockPos.CODEC.optionalFieldOf("Position2", BlockPos.of(0L)).forGetter(AreaData::pos2)
    ).apply(instance, AreaData::new));

    public boolean isPlayerInArea(Player player) {
        return isInArea(new Vec3(player.getX(), player.getY(), player.getZ()));
    }

    public boolean isBlockPosInArea(BlockPos blockPos) {
        return isInArea(new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

    public boolean isEntityInArea(Entity entity) {
        return isInArea(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
    }

    public boolean isInArea(Vec3 pos) {
        return getAABB().contains(pos);
    }

    public AABB getAABB(){
        return new AABB(
                Math.min(pos1.getX() - 1, pos2.getX() - 1),
                Math.min(pos1.getY() - 1, pos2.getY() - 1),
                Math.min(pos1.getZ() - 1, pos2.getZ() - 1),
                Math.max(pos1.getX() + 1, pos2.getX() + 1),
                Math.max(pos1.getY() + 1, pos2.getY() + 1),
                Math.max(pos1.getZ() + 1, pos2.getZ() + 1)
        );
    }

    //TODO
    public void renderArea(MultiBufferSource multiBufferSource, PoseStack poseStack) {
        VertexConsumer vertexconsumer = multiBufferSource.getBuffer(RenderType.lines());
        AABB aabb = getAABB();
        LevelRenderer.renderLineBox(poseStack, vertexconsumer,
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ,
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F
        );
    }
}