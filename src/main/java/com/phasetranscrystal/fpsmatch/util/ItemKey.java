package com.phasetranscrystal.fpsmatch.util;

import net.minecraft.world.item.ItemStack;

public class ItemKey {
    private final ItemStack stack;
    private final int hash;

    public ItemKey(ItemStack stack) {
        this.stack = stack.copyWithCount(1);
        this.hash = ItemStack.hashItemAndComponents(this.stack);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemKey itemKey = (ItemKey) o;
        return ItemStack.isSameItemSameComponents(this.stack, itemKey.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
