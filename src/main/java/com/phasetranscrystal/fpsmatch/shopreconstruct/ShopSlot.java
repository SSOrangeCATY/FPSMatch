package com.phasetranscrystal.fpsmatch.shopreconstruct;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ShopSlot {
    public final Supplier<ItemStack> itemSupplier;
    public final Predicate<ItemStack> returningChecker;
    public final int defaultCost;

    private int cost;
    private int groupId = -1;
    private int boughtCount = 0;
    private int maxBuyCount = 1;
    private boolean locked = false;

    private int index = -1;

    public int getCost() {
        return cost;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getBoughtCount() {
        return boughtCount;
    }

    public int getMaxBuyCount() {
        return maxBuyCount;
    }

    public boolean isLocked() {
        return locked;
    }

    public ItemStack process() {
        return itemSupplier.get();
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void resetCost() {
        cost = defaultCost;
    }

    public void lockReturn() {
        locked = true;
        this.boughtCount = 0;
    }

    public void unlockReturn() {
        locked = false;
    }

    public void setMaxBuyCount(int maxBuyCount) {
        this.maxBuyCount = maxBuyCount;
    }

    public boolean haveGroup(){
        return groupId >= 0;
    }

    void setIndex(int index){
        if(this.index < 0){
            this.index = index;
        }
    }

    public int getIndex(){
        return index;
    }


    public ShopSlot(ItemStack itemStack, int defaultCost) {
        this.itemSupplier = itemStack::copy;
        this.defaultCost = defaultCost;
        this.cost = defaultCost;
        this.returningChecker = stack -> stack.getDisplayName().equals(itemStack.getDisplayName());//TODO
    }

    public ShopSlot(ItemStack itemStack, int defaultCost, int maxBuyCount) {
        this(itemStack, defaultCost);
        this.maxBuyCount = maxBuyCount;
    }

    public ShopSlot(Supplier<ItemStack> supplier, int defaultCost, int maxBuyCount, int groupId, Predicate<ItemStack> checker) {
        this.itemSupplier = supplier;
        this.defaultCost = defaultCost;
        this.cost = defaultCost;
        this.maxBuyCount = maxBuyCount;
        this.groupId = groupId;
        this.returningChecker = checker;
    }

    public boolean canBuy(int money) {
        return money >= cost && boughtCount < maxBuyCount;
    }

    public boolean canReturn() {
        return boughtCount > 0 && !locked;
    }

    public void reset() {
        cost = defaultCost;
        boughtCount = 0;
        locked = false;
    }

    //flag规范：>0表示购入，<0表示返回.数值对应数量。
    public void onGroupSlotChanged(ShopSlot changedSlot, Player player, List<ShopSlot> grouped, int flag) {
    }

    //不要直接使用！从ShopData层判定与调用
    @Deprecated
    public int buy(Player player, int money) {
        boughtCount++;
        player.getInventory().add(process());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return money - cost;
    }

    //同上
    @Deprecated
    public int returnItem(Player player) {
        return returnItem(player, 1);
    }

    @Deprecated
    public int returnItem(Player player, int count) {
        count = Math.min(boughtCount, count);
        int im = player.getInventory().clearOrCountMatchingItems(returningChecker, count, player.inventoryMenu.getCraftSlots());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        boughtCount -= count;
        return count;
    }
}
