package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, GunMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GUN = SOUNDS.register("gun", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TARGET_HIT = SOUNDS.register("target_block_hit", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "target_block_hit")));
}
