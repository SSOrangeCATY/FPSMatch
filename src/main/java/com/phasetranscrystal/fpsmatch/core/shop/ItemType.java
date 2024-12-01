package com.phasetranscrystal.fpsmatch.core.shop;

public enum ItemType {
    EQUIPMENT(0),PISTOL(1),MID_RANK(2),RIFLE(3),THROWABLE(4);
    public final int typeIndex;

    ItemType(int typeIndex) {
        this.typeIndex = typeIndex;
    }
}