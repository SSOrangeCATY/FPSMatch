package com.phasetranscrystal.fpsmatch.core.entity;

import net.minecraft.world.entity.LivingEntity;

public interface BlastBombEntity {
    boolean isDeleting();
    LivingEntity getOwner();
    LivingEntity getDemolisher();
}
