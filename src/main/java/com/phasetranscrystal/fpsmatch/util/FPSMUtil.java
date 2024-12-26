package com.phasetranscrystal.fpsmatch.util;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class FPSMUtil {

    /**
     * use dummy ammo
     * */
    public static void fixGunItem( @NotNull ItemStack itemStack, @NotNull IGun iGun) {
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(gunIndexOptional.isPresent()){
            int maxAmmon = gunIndexOptional.get().getBulletData().getBulletAmount();
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
            int maxAmmon = gunIndex.getBulletData().getBulletAmount();
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
