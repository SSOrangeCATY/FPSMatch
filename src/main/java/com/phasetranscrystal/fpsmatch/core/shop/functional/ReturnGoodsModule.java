package com.phasetranscrystal.fpsmatch.core.shop.functional;

import com.phasetranscrystal.fpsmatch.core.shop.ShopSlotChangeEvent;

public class ReturnGoodsModule implements ListenerModule{
    @Override
    public void handle(ShopSlotChangeEvent event) {
        if(event.shopSlot.canReturn(event.player)){
            event.addMoney(event.shopSlot.getCost());
            event.shopSlot.returnItem(event.player);
        }
    }

    @Override
    public String getName() {
        return "returnGoods";
    }
}
