package com.phasetranscrystal.fpsmatch.mixin.accessor;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClientPacketListener.class, remap = false)
public interface ClientPacketListenerAccessor {
    @Accessor(value = "random", remap = false)
    RandomSource getRandom();
}
