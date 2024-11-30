package com.phasetranscrystal.fpsmatch.core.shop;

public enum ItemType implements GroupIndex {
    EQUIPMENT(0),PISTOL(1),MID_RANK(2),RIFLE(3),THROWABLE(4);
    public final int typeIndex;

    ItemType(int typeIndex) {
        this.typeIndex = typeIndex;
    }

    @Override
    public int getIndex() {
        return this.typeIndex;
    }
}