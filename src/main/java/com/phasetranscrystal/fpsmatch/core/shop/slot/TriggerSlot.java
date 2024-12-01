package com.phasetranscrystal.fpsmatch.core.shop.slot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TriggerSlot extends ShopSlot {
    public int triggerIndex;
    public int changedCost;

    public TriggerSlot(Supplier<ItemStack> supplier, int defaultCost, int maxBuyCount, int groupId, Predicate<ItemStack> checker) {
        super(supplier, defaultCost, maxBuyCount, groupId, checker);
    }

    public TriggerSlot(Supplier<ItemStack> supplier, int defaultCost, int maxBuyCount, int groupId, Predicate<ItemStack> checker, int changedCost, int triggerIndex) {
        super(supplier, defaultCost, maxBuyCount, groupId, checker);
        this.changedCost = changedCost;
        this.triggerIndex = triggerIndex;
    }


    public int getTriggerIndex() {
        return triggerIndex;
    }

    public int getChangedCost() {
        return changedCost;
    }

    void setTrigger(int triggerIndex,int changedCost){
        this.triggerIndex = triggerIndex;
        this.changedCost = changedCost;
    }

    @Override
    public void onGroupSlotChanged(ShopSlot changedSlot, Player player, List<ShopSlot> grouped, int flag) {
        if(changedSlot.getIndex() == this.triggerIndex){
            this.setCost(this.changedCost);
        }
    }
}
