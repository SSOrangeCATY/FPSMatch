package com.tacz.guns.client.renderer.item;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record AmmoBoxItemTintSource() implements ItemTintSource {
    public static final MapCodec<AmmoBoxItemTintSource> MAP_CODEC = MapCodec.unit(new AmmoBoxItemTintSource());

    @Override
    public int calculate(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return AmmoBoxItem.getColor(itemStack, 0);
    }

    @Override
    public MapCodec<AmmoBoxItemTintSource> type() {
        return MAP_CODEC;
    }
}
