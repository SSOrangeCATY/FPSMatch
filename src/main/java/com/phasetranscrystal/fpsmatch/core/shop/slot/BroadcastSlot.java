package com.phasetranscrystal.fpsmatch.core.shop.slot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BroadcastSlot extends ShopSlot{
    public BroadcastSlot(ItemStack itemStack, int defaultCost) {
        super(itemStack, defaultCost);
    }

    public BroadcastSlot(ItemStack itemStack, int defaultCost, int maxBuyCount) {
        super(itemStack, defaultCost, maxBuyCount);
    }

    public BroadcastSlot(Supplier<ItemStack> supplier, int defaultCost, int maxBuyCount, int groupId, Predicate<ItemStack> checker) {
        super(supplier, defaultCost, maxBuyCount, groupId, checker);
    }

    @Override
    public void onGroupSlotChanged(ShopSlot changedSlot, Player player, List<ShopSlot> grouped, int flag) {
        if(changedSlot.getIndex() != this.getIndex()){
            this.returnItem(player,this.getBoughtCount());
        }
    }
}
