package com.phasetranscrystal.fpsmatch.core.shop;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ShopUtil {
    public static boolean checkItemStackIsInPlayerInventory(ItemStack itemStack, Player player){
        if(itemStack.getItem() instanceof IGun iGun){
            ResourceLocation gunId = iGun.getGunId(itemStack);
            int i = player.getInventory().clearOrCountMatchingItems((item)->{
                if(item.getItem() instanceof IGun iGun1){
                    return iGun1.getGunId(item).equals(gunId);
                }
                return false;
            },0,player.inventoryMenu.getCraftSlots());
            return i > 0;
        }else {
            int i = player.getInventory().clearOrCountMatchingItems((item)->
                    item.getItem() == itemStack.getItem(),0,player.inventoryMenu.getCraftSlots());
            return i > 0;
        }
    }
}
