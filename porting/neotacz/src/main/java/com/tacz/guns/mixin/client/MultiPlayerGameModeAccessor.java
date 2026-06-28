package com.tacz.guns.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MultiPlayerGameMode.class, remap = false)
public interface MultiPlayerGameModeAccessor {
    @Invoker(value = "ensureHasSentCarriedItem", remap = false)
    void tacz$ensureHasSentCarriedItem();
}
