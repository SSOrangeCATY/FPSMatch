package com.phasetranscrystal.fpsmatch.core.shop;

import net.minecraft.world.item.ItemStack;

public class ClientShopData {
    String displayName;
    ItemStack itemStack;
    int cost;
    boolean canBuy;
    boolean canReturn;

    public ClientShopData(String displayName, ItemStack itemStack, int cost) {
        this.displayName = displayName;
        this.itemStack = itemStack;
        this.cost = cost;
        this.canBuy = true;
        this.canReturn = false;
    }

    public String getDisplayName() {
        return displayName;
    }
    public ItemStack getItemStack() {
        return itemStack;
    }
    public int getCost() {
        return cost;
    }
    public boolean canBuy() {
        return canBuy;
    }
    public boolean canReturn() {
        return canReturn;
    }
}
