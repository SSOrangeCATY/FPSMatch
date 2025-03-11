package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.client.shop.ClientShopSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ShopContainer implements Container {
    public final ClientShopSlot slot;

    public ShopContainer(ClientShopSlot slot) {
        this.slot = slot;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.slot.itemStack().isEmpty();
    }

    @Override
    public ItemStack getItem(int i) {
        return this.slot.itemStack();
    }

    @Override
    public ItemStack removeItem(int i, int i1) {
        ItemStack itemStack = this.slot.itemStack();
        if (!itemStack.isEmpty()) {
            if (itemStack.getCount() > i1) {
                itemStack.shrink(i1);
                this.slot.setItemStack(itemStack);
                return itemStack;
            } else {
                this.slot.setItemStack(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int i) {
        ItemStack itemStack = this.slot.itemStack();
        if (!itemStack.isEmpty()) {
            if (itemStack.getCount() > i) {
                itemStack.shrink(i);
                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int i, ItemStack itemStack) {
        this.slot.setItemStack(itemStack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.slot.setItemStack(ItemStack.EMPTY);
    }
}
