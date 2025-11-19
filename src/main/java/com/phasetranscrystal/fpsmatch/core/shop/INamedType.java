package com.phasetranscrystal.fpsmatch.core.shop;

import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;

import java.util.ArrayList;

public interface INamedType {
    String name();
    int slotCount();
    boolean dorpUnlock();
    ArrayList<ShopSlot> defaultSlots();
}
