package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class SpawnPointData {
    ResourceKey<Level> dimension;BlockPos position; float angle; boolean forced; boolean sendMessage;
    public SpawnPointData(ResourceKey<Level> pDimension, @Nullable BlockPos pPosition, float pAngle, boolean pForced, boolean pSendMessage){
        this.dimension = pDimension;
        this.position = pPosition;
        this.angle = pAngle;
        this.forced = pForced;
        this.sendMessage = pSendMessage;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public boolean isForced() {
        return forced;
    }

    public boolean isSendMessage() {
        return sendMessage;
    }

    public float getAngle() {
        return angle;
    }
}
