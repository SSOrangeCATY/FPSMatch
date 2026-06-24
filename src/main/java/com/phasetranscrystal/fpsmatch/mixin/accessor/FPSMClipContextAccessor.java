package com.phasetranscrystal.fpsmatch.mixin.accessor;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClipContext.class, remap = false)
public interface FPSMClipContextAccessor {
    @Accessor(remap = false)
    CollisionContext getCollisionContext();
}
