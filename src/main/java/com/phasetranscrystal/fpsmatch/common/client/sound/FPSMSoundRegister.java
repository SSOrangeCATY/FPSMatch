package com.phasetranscrystal.fpsmatch.common.client.sound;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.tacz.guns.api.item.GunTabType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class FPSMSoundRegister {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FPSMatch.MODID);
    public static final RegistryObject<SoundEvent> VOICE_SMOKE = SOUNDS.register("voice_smoke", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_smoke")));
    public static final RegistryObject<SoundEvent> VOICE_FLASH = SOUNDS.register("voice_flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_flash")));
    public static final RegistryObject<SoundEvent> VOICE_GRENADE = SOUNDS.register("voice_grenade", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_grenade")));
    public static final RegistryObject<SoundEvent> FLASH = SOUNDS.register("flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "flash")));
    public static final RegistryObject<SoundEvent> BOOM = SOUNDS.register("boom", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "boom")));

    private static final Map<String,RegistryObject<SoundEvent>> FPSM_SOUNDS = initSounds();

    private static Map<String,RegistryObject<SoundEvent>> initSounds(){
        Map<String,RegistryObject<SoundEvent>> map = new HashMap<>();
        for (GunTabType type : GunTabType.values()) {
            String name = type.name().toLowerCase();
            for(SoundType soundType : SoundType.values()) {
                String action = soundType.name().toLowerCase();
                String n = name+"_"+action;
                RegistryObject<SoundEvent> event = SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, n)));
                map.put(n,event);
            }
        }
        return map;
    }

    public static RegistryObject<SoundEvent> getGunSound(GunTabType gunType,SoundType soundType){
        return FPSM_SOUNDS.get(gunType.name().toLowerCase()+"_"+soundType.name().toLowerCase());
    }

    public enum SoundType{
        DORP_PICKUP,
        SHOP_BOUGHT
    }
}
