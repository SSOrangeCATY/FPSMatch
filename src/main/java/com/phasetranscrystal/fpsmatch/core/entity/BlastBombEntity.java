package com.phasetranscrystal.fpsmatch.core.entity;

import com.phasetranscrystal.fpsmatch.core.map.BlastBombState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;

public abstract class BlastBombEntity extends Entity implements TraceableEntity {

    public BlastBombEntity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public abstract boolean isDeleting();
    public abstract LivingEntity getOwner();
    public abstract LivingEntity getDemolisher();
    public abstract BlastBombState getState();
}
