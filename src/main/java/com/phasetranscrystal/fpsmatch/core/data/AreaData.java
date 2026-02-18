package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;


public class AreaData {
    public static final Codec<AreaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("Position1", BlockPos.of(0L)).forGetter(AreaData::pos1),
            BlockPos.CODEC.optionalFieldOf("Position2", BlockPos.of(0L)).forGetter(AreaData::pos2)
    ).apply(instance, AreaData::new));

    private final BlockPos pos1;
    private final BlockPos pos2;
    private final AABB aabb;

    public AreaData(@Nonnull BlockPos pos1,@Nonnull BlockPos pos2){
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.aabb = new AABB(pos1, pos2);
    }

    public BlockPos pos1() {
        return pos1;
    }

    public BlockPos pos2() {
        return pos2;
    }

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
        return aabb.contains(pos);
    }

    public AABB aabb(){
        return aabb;
    }

    public void renderArea(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.lines());
        AABB aabb = aabb();

        LevelRenderer.renderLineBox(poseStack, vertexconsumer,
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ,
                1F, 1F, 0, 1.0F,
                0.5F, 0.5F, 0.5F
        );
    }
}