package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FPSMSoundRegister {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FPSMatch.MODID);
    public static RegistryObject<SoundEvent> beep = SOUNDS.register("beep", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "beep")));
    public static RegistryObject<SoundEvent> planting = SOUNDS.register("planting", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "planting")));
    public static RegistryObject<SoundEvent> planted = SOUNDS.register("planted", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "planted")));
    public static RegistryObject<SoundEvent> defused = SOUNDS.register("defused", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "defused")));
    public static RegistryObject<SoundEvent> click = SOUNDS.register("buttons_click", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "buttons_click")));
}
