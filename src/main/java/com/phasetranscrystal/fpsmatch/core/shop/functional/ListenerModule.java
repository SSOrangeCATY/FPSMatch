package com.phasetranscrystal.fpsmatch.core.shop.functional;

import com.phasetranscrystal.fpsmatch.core.shop.event.ShopSlotChangeEvent;

public interface ListenerModule {
    void handle(ShopSlotChangeEvent event);
    String getName();
    int getPriority();
}
