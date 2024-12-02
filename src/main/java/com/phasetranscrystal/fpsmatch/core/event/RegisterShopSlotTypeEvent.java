package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.shop.ShopManager;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.NotNull;

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

    public <T extends ShopSlot> void registerSlotCodec(@NotNull Class<T> type, @NotNull Codec<T> codec){
        this.shopManager.registerSlotCodec(type,codec);
    }
}
