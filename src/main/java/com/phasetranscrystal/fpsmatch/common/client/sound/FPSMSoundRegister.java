package com.phasetranscrystal.fpsmatch.common.client.sound;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FPSMSoundRegister {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FPSMatch.MODID);
    public static RegistryObject<SoundEvent> voice_smoke = SOUNDS.register("voice_smoke", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_smoke")));
    public static RegistryObject<SoundEvent> voice_flash = SOUNDS.register("voice_flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_flash")));
    public static RegistryObject<SoundEvent> voice_grenade = SOUNDS.register("voice_grenade", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_grenade")));
    public static RegistryObject<SoundEvent> flash = SOUNDS.register("flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "flash")));
    public static RegistryObject<SoundEvent> boom = SOUNDS.register("boom", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "boom")));

}
