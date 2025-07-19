package com.phasetranscrystal.fpsmatch.core.shop;

public record UnknownShopType(String name,int slotCount,boolean dorpUnlock) implements INamedType {
    public UnknownShopType(String name) {
        this(name, 0, false);
    }
}
