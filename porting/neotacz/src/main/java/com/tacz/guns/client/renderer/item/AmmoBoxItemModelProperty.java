package com.tacz.guns.client.renderer.item;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record AmmoBoxItemModelProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<AmmoBoxItemModelProperty> MAP_CODEC = MapCodec.unit(new AmmoBoxItemModelProperty());

    @Override
    public float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        return Mth.clamp(AmmoBoxItem.getStatue(itemStack, level, owner == null ? null : owner.asLivingEntity(), seed), 0.0F, 8.0F);
    }

    @Override
    public MapCodec<AmmoBoxItemModelProperty> type() {
        return MAP_CODEC;
    }
}
