package com.phasetranscrystal.fpsmatch.core.shop.skin;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class SkinHandler {

    public static void applySkin(JsonElement jsonElement, ShopSlot shopSlot) {
        SkinType skinType = SkinType.valueOf(jsonElement.getAsJsonObject().get("type").toString());
        JsonElement data = jsonElement.getAsJsonObject().get("data");
        ItemStack itemStack = shopSlot.process();

        switch (skinType) {
            case GUN_ID : {
                if(itemStack.getItem() instanceof IGun iGun){
                    iGun.setGunId(itemStack,new ResourceLocation(data.toString()));
                }
                break;
            }
            case GUN_DISPLAY_ID : {
                if(itemStack.getItem() instanceof IGun iGun){
                    iGun.setGunDisplayId(itemStack,new ResourceLocation(data.toString()));
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
