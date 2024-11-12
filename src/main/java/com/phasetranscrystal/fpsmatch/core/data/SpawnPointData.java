package com.phasetranscrystal.fpsmatch.core.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class SpawnPointData {
    ResourceKey<Level> dimension;
    BlockPos position;
    float pYaw;
    float pPitch;

    public SpawnPointData(ResourceKey<Level> pDimension, @Nullable BlockPos pPosition, float pYaw, float pPitch) {
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

    @Override
    public String toString() {
        return dimension.location().getPath() + " " + position.toString();
    }

    public void save(CompoundTag pCompoundTag) {
        pCompoundTag.putString("Dimension", this.dimension.location().toString());

        if (this.position != null) {
            pCompoundTag.putLong("Position", this.position.asLong());
        }

        pCompoundTag.putFloat("Yaw", this.pYaw);
        pCompoundTag.putFloat("Pitch", this.pPitch);
    }

    public static SpawnPointData load(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dimension")));
        BlockPos position = tag.contains("Position") ? BlockPos.of(tag.getLong("Position")) : null;
        float pYaw = tag.getFloat("Yaw");
        float pPitch = tag.getFloat("Pitch");

        return new SpawnPointData(dimension, position, pYaw, pPitch);
    }


}
