package com.phasetranscrystal.fpsmatch.compat;

import club.pisquad.minecraft.csgrenades.api.CSGrenadesAPI;
import club.pisquad.minecraft.csgrenades.item.CounterStrikeGrenadeItem;
import club.pisquad.minecraft.csgrenades.registry.ModDamageType;
import club.pisquad.minecraft.csgrenades.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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
}
