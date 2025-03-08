package com.phasetranscrystal.fpsmatch.util;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class FPSMUtil {

    public static void setTotalDummyAmmo(ItemStack itemStack, IGun iGun, int amount){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            iGun.useDummyAmmo(itemStack);
            if(amount - maxAmmon > 0) {
                iGun.setCurrentAmmoCount(itemStack,maxAmmon);
                int dummy = amount - maxAmmon;
                iGun.setMaxDummyAmmoAmount(itemStack,dummy);
                iGun.setDummyAmmoAmount(itemStack, dummy);
            }else{
                iGun.setCurrentAmmoCount(itemStack,amount);
                iGun.setDummyAmmoAmount(itemStack,0);
                iGun.setMaxDummyAmmoAmount(itemStack,0);
            }
        }
    }


    public static int getTotalDummyAmmo(ItemStack itemStack, IGun iGun){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            int dummy = iGun.getMaxDummyAmmoAmount(itemStack);
            return maxAmmon + dummy;
        }
        return 0;
    }

    /**
     * use dummy ammo
     * */
    public static void fixGunItem( @NotNull ItemStack itemStack, @NotNull IGun iGun) {
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(gunIndexOptional.isPresent()){
            int maxAmmon = gunIndexOptional.get().getGunData().getAmmoAmount();
            iGun.setCurrentAmmoCount(itemStack,maxAmmon);
        }
        int maxAmmo = iGun.getMaxDummyAmmoAmount(itemStack);
        if(maxAmmo > 0) {
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,maxAmmo);
        }
    }

    /**
     * use dummy ammo
     * */
    public static void resetGunAmmo(ItemStack itemStack, IGun iGun){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            iGun.setCurrentAmmoCount(itemStack,maxAmmon);
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,iGun.getMaxDummyAmmoAmount(itemStack));
        }
    }


    /**
    *  use dummy ammo
    * */
    public static void resetAllGunAmmo(@NotNull ServerPlayer serverPlayer){
        Inventory inventory = serverPlayer.getInventory();
        List<NonNullList<ItemStack>> compartments = ImmutableList.of(inventory.items, inventory.armor, inventory.offhand);
        compartments.forEach((itemList)-> itemList.forEach(itemStack -> {
            if(itemStack.getItem() instanceof IGun iGun){
                resetGunAmmo(itemStack,iGun);
            }
        }));
    }


}
