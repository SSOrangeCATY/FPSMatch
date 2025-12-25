package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SpawnPointData {
    public static final Codec<SpawnPointData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("Dimension").forGetter(spawnPointData -> spawnPointData.getDimension().location().toString()),
            Vec3.CODEC.fieldOf("Position").forGetter(SpawnPointData::getPosition),
            Codec.FLOAT.fieldOf("Yaw").forGetter(SpawnPointData::getYaw),
            Codec.FLOAT.fieldOf("Pitch").forGetter(SpawnPointData::getPitch)
    ).apply(instance, (dimensionStr, position, yaw, pitch) -> {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionStr));
        return new SpawnPointData(dimension, position, yaw, pitch);
    }));

    ResourceKey<Level> dimension;
    Vec3 position;
    float pYaw;
    float pPitch;

    public SpawnPointData(ResourceKey<Level> pDimension, Vec3 position, float pYaw, float pPitch) {
        this.dimension = pDimension;
        this.position = position;
        this.pYaw = pYaw;
        this.pPitch = pPitch;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public Vec3 getPosition() {
        return position;
    }

    public BlockPos getBlockPos(){
        return BlockPos.containing(position);
    }

    public float getPitch() {
        return pPitch;
    }

    public float getYaw() {
        return pYaw;
    }

    public double getX(){
        return position.x();
    }
    public double getY(){
        return position.y();
    }
    public double getZ(){
        return position.z();
    }

    @Override
    public String toString() {
        return dimension.location().getPath() + " " + position.toString();
    }
}
