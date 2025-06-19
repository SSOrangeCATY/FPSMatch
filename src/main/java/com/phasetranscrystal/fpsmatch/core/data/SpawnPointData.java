package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class SpawnPointData {
    public static final Codec<SpawnPointData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("Dimension").forGetter(spawnPointData -> spawnPointData.getDimension().location().toString()),
            BlockPos.CODEC.optionalFieldOf("Position", BlockPos.of(0L)).forGetter(SpawnPointData::getPosition),
            Codec.FLOAT.fieldOf("Yaw").forGetter(SpawnPointData::getYaw),
            Codec.FLOAT.fieldOf("Pitch").forGetter(SpawnPointData::getPitch)
    ).apply(instance, (dimensionStr, position, yaw, pitch) -> {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionStr));
        return new SpawnPointData(dimension, position, yaw, pitch);
    }));

    ResourceKey<Level> dimension;
    BlockPos position;
    float pYaw;
    float pPitch;

    public SpawnPointData(ResourceKey<Level> pDimension, BlockPos pPosition, float pYaw, float pPitch) {
        this.dimension = pDimension;
        this.position = pPosition;
        this.pYaw = pYaw;
        this.pPitch = pPitch;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public float getPitch() {
        return pPitch;
    }

    public float getYaw() {
        return pYaw;
    }

    public int getX(){
        return position.getX();
    }
    public int getY(){
        return position.getY();
    }
    public int getZ(){
        return position.getZ();
    }

    @Override
    public String toString() {
        return dimension.location().getPath() + " " + position.toString();
    }
}
