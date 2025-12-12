package com.phasetranscrystal.fpsmatch.core.entity;

import com.phasetranscrystal.fpsmatch.core.map.BlastBombState;
import net.minecraft.world.entity.LivingEntity;

public interface BlastBombEntity {
    boolean isDeleting();
    LivingEntity getOwner();
    LivingEntity getDemolisher();
    BlastBombState getState();
    boolean isRemoved();
    void discard();
}
