package com.phasetranscrystal.fpsmatch.common.effect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class FPSMEffectRegister {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, FPSMatch.MODID);
    public static final DeferredHolder<MobEffect, MobEffect> FLASH_BLINDNESS = MOB_EFFECTS.register("flash_blindness",
            () -> new FlashBlindnessMobEffect(MobEffectCategory.HARMFUL));
}
