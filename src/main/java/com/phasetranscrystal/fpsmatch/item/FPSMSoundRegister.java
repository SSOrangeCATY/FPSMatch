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
    public static RegistryObject<SoundEvent> voice_smoke = SOUNDS.register("voice_smoke", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_smoke")));
    public static RegistryObject<SoundEvent> voice_flash = SOUNDS.register("voice_flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_flash")));
    public static RegistryObject<SoundEvent> voice_grenade = SOUNDS.register("voice_grenade", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_grenade")));
    public static RegistryObject<SoundEvent> voice_t_win = SOUNDS.register("voice_ct_win", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_ct_win")));
    public static RegistryObject<SoundEvent> voice_ct_win = SOUNDS.register("voice_t_win", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_t_win")));
    public static RegistryObject<SoundEvent> flash = SOUNDS.register("flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "flash")));
    public static RegistryObject<SoundEvent> boom = SOUNDS.register("boom", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "boom")));

}
