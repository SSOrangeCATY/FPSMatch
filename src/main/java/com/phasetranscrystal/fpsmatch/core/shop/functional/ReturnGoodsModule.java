package com.phasetranscrystal.fpsmatch.core.shop.functional;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.shop.event.ShopSlotChangeEvent;

public class ReturnGoodsModule implements ListenerModule {
    @Override
    public void handle(ShopSlotChangeEvent event) {
        if(event.flag >= 1 && event.shopSlot.canReturn(event.player)){
            event.addMoney(event.shopSlot.getCost());
            event.shopSlot.returnItem(event.player);
        }
    }

    @Override
    public String getName() {
        return "returnGoods";
    }

}
