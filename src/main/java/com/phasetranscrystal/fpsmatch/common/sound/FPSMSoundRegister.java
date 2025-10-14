package com.phasetranscrystal.fpsmatch.common.sound;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.tacz.guns.api.item.GunTabType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FPSMSoundRegister {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FPSMatch.MODID);

    public static final RegistryObject<SoundEvent> VOICE_SMOKE = SOUNDS.register("voice_smoke", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_smoke")));
    public static final RegistryObject<SoundEvent> VOICE_FLASH = SOUNDS.register("voice_flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_flash")));
    public static final RegistryObject<SoundEvent> VOICE_GRENADE = SOUNDS.register("voice_grenade", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "voice_grenade")));
    public static final RegistryObject<SoundEvent> FLASH = SOUNDS.register("flash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "flash")));
    public static final RegistryObject<SoundEvent> BOOM = SOUNDS.register("boom", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FPSMatch.MODID, "boom")));

    private static final Map<GunTabType, SoundEvent> GUN_PICKUP_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<GunTabType, SoundEvent> GUN_BOUGHT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<GunTabType, SoundEvent> GUN_DROP_REGISTRY = new ConcurrentHashMap<>();

    private static final Map<Item, SoundEvent> ITEM_PICKUP_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Item, SoundEvent> ITEM_DROP_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Item, SoundEvent> ITEM_BOUGHT_REGISTRY = new ConcurrentHashMap<>();

    private static SoundEvent KNIFE_PICKUP_SOUND = SoundEvents.ITEM_PICKUP;
    private static SoundEvent KNIFE_DROP_SOUND = SoundEvents.STONE_BUTTON_CLICK_ON;
    private static SoundEvent KNIFE_BOUGHT_SOUND = SoundEvents.STONE_HIT;

    public static void registerKnifePickupSound(SoundEvent sound) {
        KNIFE_PICKUP_SOUND = sound;
    }

    public static void registerKnifeDropSound(SoundEvent sound) {
        KNIFE_DROP_SOUND = sound;
    }

    public static void registerKnifeBoughtSound(SoundEvent sound) {
        KNIFE_BOUGHT_SOUND = sound;
    }

    public static SoundEvent getKnifePickupSound() {
        return KNIFE_PICKUP_SOUND;
    }

    public static SoundEvent getKnifeDropSound() {
        return KNIFE_DROP_SOUND;
    }

    public static SoundEvent getKnifeBoughtSound() {
        return KNIFE_BOUGHT_SOUND;
    }

    public static void registerKnifeSounds(SoundEvent pickupSound, SoundEvent dropSound, SoundEvent boughtSound) {
        registerKnifePickupSound(pickupSound);
        registerKnifeDropSound(dropSound);
        registerKnifeBoughtSound(boughtSound);
    }

    public static SoundEvent getItemPickSound(Item item) {
        return ITEM_PICKUP_REGISTRY.getOrDefault(item, SoundEvents.ITEM_PICKUP);
    }

    public static void registerItemPickupSound(Item item, SoundEvent sound) {
        ITEM_PICKUP_REGISTRY.put(item, sound);
    }

    public static void registerItemDropSound(Item item, SoundEvent sound) {
        ITEM_DROP_REGISTRY.put(item, sound);
    }

    public static SoundEvent getItemDropSound(Item item) {
        return ITEM_DROP_REGISTRY.getOrDefault(item, SoundEvents.STONE_BUTTON_CLICK_ON);
    }

    public static SoundEvent getItemBoughtSound(Item item) {
        return ITEM_BOUGHT_REGISTRY.getOrDefault(item, SoundEvents.STONE_HIT);
    }

    public static void registerItemBoughtSound(Item item, SoundEvent sound) {
        ITEM_BOUGHT_REGISTRY.put(item, sound);
    }

    public static SoundEvent getGunPickupSound(GunTabType gunType) {
        return GUN_PICKUP_REGISTRY.getOrDefault(gunType, SoundEvents.ITEM_PICKUP);
    }

    public static void registerGunPickupSound(GunTabType gunType, SoundEvent sound) {
        GUN_PICKUP_REGISTRY.put(gunType, sound);
    }

    public static SoundEvent getGunBoughtSound(GunTabType gunType) {
        return GUN_BOUGHT_REGISTRY.getOrDefault(gunType, SoundEvents.STONE_HIT);
    }

    public static void registerGunBoughtSound(GunTabType gunType, SoundEvent sound) {
        GUN_BOUGHT_REGISTRY.put(gunType, sound);
    }

    public static SoundEvent getGunDropSound(GunTabType gunType) {
        return GUN_DROP_REGISTRY.getOrDefault(gunType, SoundEvents.STONE_BUTTON_CLICK_ON);
    }

    public static void registerGunDropSound(GunTabType gunType, SoundEvent sound) {
        GUN_DROP_REGISTRY.put(gunType, sound);
    }

    public static void registerGunSounds(GunTabType gunType, SoundEvent pickupSound, SoundEvent boughtSound, SoundEvent dropSound) {
        registerGunPickupSound(gunType, pickupSound);
        registerGunBoughtSound(gunType, boughtSound);
        registerGunDropSound(gunType, dropSound);
    }

    public static void registerItemSounds(Item item, SoundEvent pickupSound, SoundEvent dropSound, SoundEvent boughtSound) {
        registerItemPickupSound(item, pickupSound);
        registerItemDropSound(item, dropSound);
        registerItemBoughtSound(item, boughtSound);
    }
}