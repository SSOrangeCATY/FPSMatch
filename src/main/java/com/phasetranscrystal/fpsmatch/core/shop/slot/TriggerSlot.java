package com.phasetranscrystal.fpsmatch.core.shop.slot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TriggerSlot extends ShopSlot {
    public final int triggerIndex;

    public TriggerSlot(ItemStack itemStack, int defaultCost, int maxBuyCount, int groupId, int triggerIndex) {
        super(itemStack, defaultCost, maxBuyCount, groupId);
        this.triggerIndex = triggerIndex;
    }

    public int getTriggerIndex() {
        return triggerIndex;
    }

    @Override
    public Codec<TriggerSlot> getCodec(){
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("Type").forGetter(TriggerSlot::getType),
                ItemStack.CODEC.fieldOf("ItemStack").forGetter(TriggerSlot::process),
                Codec.INT.fieldOf("DefaultCost").forGetter(TriggerSlot::getDefaultCost),
                Codec.INT.fieldOf("GroupId").forGetter(TriggerSlot::getGroupId),
                Codec.INT.fieldOf("MaxBuyCount").forGetter(TriggerSlot::getMaxBuyCount),
                Codec.INT.fieldOf("TriggerIndex").forGetter(TriggerSlot::getTriggerIndex)
        ).apply(instance, (type,itemstack,dC,gId,mB,tI) -> new TriggerSlot(itemstack,dC,gId,mB,tI)));
    }
}
