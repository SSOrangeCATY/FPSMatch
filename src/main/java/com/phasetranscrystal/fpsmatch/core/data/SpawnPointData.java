package com.phasetranscrystal.fpsmatch.core.data;

import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class SpawnPointData {
    ResourceKey<Level> dimension;BlockPos position; float pYaw; float pPitch;
    public SpawnPointData(ResourceKey<Level> pDimension, @Nullable BlockPos pPosition, float pYaw, float pPitch){
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
}
