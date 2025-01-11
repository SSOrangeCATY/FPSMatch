package com.phasetranscrystal.fpsmatch.effect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class FPSMEffectRegister {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, FPSMatch.MODID);
    public static final RegistryObject<MobEffect> FLASH_BLINDNESS = MOB_EFFECTS.register("flash_blindness",
            () -> new FlashBlindnessMobEffect(MobEffectCategory.HARMFUL, 1.88F,4.87F));


}
