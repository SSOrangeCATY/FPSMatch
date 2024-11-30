package com.phasetranscrystal.fpsmatch.core.shop;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ShopSlot {
    public final SlotType slotType;
    public final int index;
    public final ItemStack itemStack;
    public final int defaultCost;
    public int cost;
    public int boughtCount = 0;
    public int maxBuyCount = 1;
    public boolean lockedReturn = false;
    public ShopSlot(SlotType slotType, int index, ItemStack itemStack, int defaultCost) {
        this.index = index;
        this.itemStack = itemStack.copy();
        this.defaultCost = defaultCost;
        this.cost = defaultCost;
        this.slotType = slotType;
    }

    public ShopSlot(SlotType slotType, int index, ItemStack itemStack, int defaultCost, int maxBuyCount) {
        this(slotType, index, itemStack, defaultCost);
        this.maxBuyCount = maxBuyCount;
    }

    public boolean canBuy(int money){
        return money >= cost && boughtCount < maxBuyCount;
    }

    public boolean canReturn(){
        return boughtCount > 0 && !lockedReturn;
    }

    public int getCost(){
        return cost;
    }

    public void reset(){
        cost = defaultCost;
        boughtCount = 0;
        lockedReturn = false;
    }

    public int buy(@Nullable Player player, int money){
        if(!this.canBuy(money)) return money;

        boughtCount++;
        if(player != null){
            player.getInventory().add(itemStack.copy());
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return money - cost;
    }


    public boolean returnItem(@Nullable Player player){
        if(!this.canReturn()) return false;
        if(player!= null){
            int im = player.getInventory().clearOrCountMatchingItems((i)-> i.getDisplayName().getString().equals(this.itemStack.getDisplayName().getString()), 1, player.inventoryMenu.getCraftSlots());
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if(im > 0) {
                boughtCount--;
                return true;
            }
        }
        return false;
    }

    public ItemStack ItemStack(){
        return itemStack.copy();
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void resetCost(){
        cost = defaultCost;
    }
    public void lockReturn(){
        lockedReturn = true;
        this.boughtCount = 0;
    }
    public void unlockReturn(){
        lockedReturn = false;
    }

    public void setMaxBuyCount(int maxBuyCount) {
        this.maxBuyCount = maxBuyCount;
    }
    public SlotType getSlotType() {
        return slotType;
    }

    public int getIndex() {
        return index;
    }
}
