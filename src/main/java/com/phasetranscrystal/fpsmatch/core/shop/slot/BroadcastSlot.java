package com.phasetranscrystal.fpsmatch.core.shop.slot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BroadcastSlot extends ShopSlot{
    public BroadcastSlot(ItemStack itemStack, int defaultCost, int groupId) {
        super(itemStack, defaultCost);
    }

    public BroadcastSlot(ItemStack itemStack, int defaultCost, int maxBuyCount, int groupId) {
        super(itemStack, defaultCost, maxBuyCount,groupId);
    }

    public BroadcastSlot(Supplier<ItemStack> supplier, int defaultCost, int maxBuyCount, int groupId, Predicate<ItemStack> checker) {
        super(supplier, defaultCost, maxBuyCount, groupId, checker);
    }

    @Override
    public Codec<BroadcastSlot> getCodec(){
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("Type").forGetter(ShopSlot::getType),
                ItemStack.CODEC.fieldOf("ItemStack").forGetter(ShopSlot::process),
                Codec.INT.fieldOf("defaultCost").forGetter(ShopSlot::getDefaultCost),
                Codec.INT.fieldOf("groupId").forGetter(ShopSlot::getGroupId)
        ).apply(instance, (type,itemstack,dC,gId) -> new BroadcastSlot(itemstack,dC,gId)));
    }

}
