package com.ptcrys.fpsmatch.core.shop.skin;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.ptcrys.fpsmatch.core.shop.slot.ShopSlot;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class SkinHandler {

    public static void applySkin(JsonElement jsonElement, ShopSlot shopSlot) {
        SkinType skinType = SkinType.valueOf(jsonElement.getAsJsonObject().get("type").toString());
        JsonElement data = jsonElement.getAsJsonObject().get("data");
        ItemStack itemStack = shopSlot.process();

        switch (skinType) {
            case GUN_ID : {
                if(GunCompatManager.isGun(itemStack)){
                    GunCompatManager.findProvider(itemStack).setGunId(itemStack, ResourceLocation.parse(data.toString()));
                }
                break;
            }
            case GUN_DISPLAY_ID : {
                if(GunCompatManager.isGun(itemStack)){
                    GunCompatManager.findProvider(itemStack).setGunDisplayId(itemStack, ResourceLocation.parse(data.toString()));
                }
                break;
            }
            case ITEM : {
                itemStack = ItemStack.CODEC.decode(JsonOps.INSTANCE, data).getOrThrow(false,(e)->{}).getFirst();
                break;
            }
            default : {
                break;
            }
        }
        shopSlot.setItemSupplier(itemStack::copy);
    }
}
