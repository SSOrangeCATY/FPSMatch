package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.shop.ShopManager;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraftforge.eventbus.api.Event;

public class RegisterShopSlotTypeEvent extends Event {
    ShopManager shopManager;
    public RegisterShopSlotTypeEvent(ShopManager fpsmCore){
        this.shopManager = fpsmCore;
    }
    @Override
    public boolean isCancelable()
    {
        return false;
    }

    public void register(String type, Codec<? extends ShopSlot> codec){
        this.shopManager.registerSlotCodec(type,codec);
    }
}
