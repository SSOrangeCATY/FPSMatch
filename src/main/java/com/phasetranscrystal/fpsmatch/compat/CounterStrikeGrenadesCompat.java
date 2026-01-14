package com.phasetranscrystal.fpsmatch.compat;

import club.pisquad.minecraft.csgrenades.api.CSGrenadesAPI;
import club.pisquad.minecraft.csgrenades.config.ModConfig;
import club.pisquad.minecraft.csgrenades.entity.SmokeGrenadeEntity;
import club.pisquad.minecraft.csgrenades.item.CounterStrikeGrenadeItem;
import club.pisquad.minecraft.csgrenades.registry.ModDamageType;
import club.pisquad.minecraft.csgrenades.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;


public class CounterStrikeGrenadesCompat {

    public static void registerKillIcon(Map<ResourceLocation, String> registry){
        ModItems instance = ModItems.INSTANCE;
        registry.put(ForgeRegistries.ITEMS.getKey(instance.getHEGRENADE_ITEM().get()),"grenade");
        registry.put(ForgeRegistries.ITEMS.getKey(instance.getINCENDIARY_ITEM().get()),"ct_incendiary_grenade");
        registry.put(ForgeRegistries.ITEMS.getKey(instance.getMOLOTOV_ITEM().get()),"t_incendiary_grenade");
        registry.put(ForgeRegistries.ITEMS.getKey(instance.getSMOKE_GRENADE_ITEM().get()),"smoke_shell");
        registry.put(ForgeRegistries.ITEMS.getKey(instance.getFLASH_BANG_ITEM().get()),"flash_bomb");
    }

    public static ItemStack getItemFromDamageSource(DamageSource damageSource){
        ModDamageType types = ModDamageType.INSTANCE;
        ModItems items = ModItems.INSTANCE;
        if(damageSource.is(types.getFLASHBANG_HIT())){
            return new ItemStack(items.getFLASH_BANG_ITEM().get());
        }else if(damageSource.is(types.getHEGRENADE_HIT()) || damageSource.is(types.getHEGRENADE_EXPLOSION())){
            return new ItemStack(items.getHEGRENADE_ITEM().get());
        }else if(damageSource.is(types.getINCENDIARY_HIT()) || damageSource.is(types.getINCENDIARY_FIRE())){
            return new ItemStack(items.getINCENDIARY_ITEM().get());
        }else if(damageSource.is(types.getMOLOTOV_HIT()) || damageSource.is(types.getMOLOTOV_FIRE())){
            return new ItemStack(items.getMOLOTOV_ITEM().get());
        }else if(damageSource.is(types.getSMOKEGRENADE_HIT())){
            return new ItemStack(items.getSMOKE_GRENADE_ITEM().get());
        }else if(damageSource.is(types.getDECOY_GRENADE_HIT())){
            return new ItemStack(items.getDECOY_GRENADE_ITEM().get());
        }else{
            return ItemStack.EMPTY;
        }
    }

    public static boolean itemCheck(Player player){
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return main instanceof CounterStrikeGrenadeItem || off instanceof CounterStrikeGrenadeItem;
    }

    public static boolean isPlayerFlashed(Player player){
        return CSGrenadesAPI.isPlayerFlashed(player);
    }

    public static boolean isInSmokeGrenadeArea(Level level, AABB checker , Vec3 pos) {
        List<SmokeGrenadeEntity> smokes = level.getEntitiesOfClass(SmokeGrenadeEntity.class,checker);
        for (SmokeGrenadeEntity smoke : smokes) {
            if(isInSmoke(pos, smoke)){
                return true;
            }
        }
        return false;
    }

    public static boolean isInSmoke(Vec3 position, SmokeGrenadeEntity smoke){
        double smokeRadius = ModConfig.SmokeGrenade.SMOKE_RADIUS.get().doubleValue();
        double smokeFallingHeight = ModConfig.SmokeGrenade.SMOKE_MAX_FALLING_HEIGHT.get().doubleValue();
        AABB smokeCloudBoundingBox = new AABB(smoke.blockPosition()).inflate(smokeRadius).expandTowards(0.0, -smokeFallingHeight, 0.0);
        return smokeCloudBoundingBox.contains(position);
    }
}
