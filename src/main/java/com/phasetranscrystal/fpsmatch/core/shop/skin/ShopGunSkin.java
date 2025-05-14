package com.phasetranscrystal.fpsmatch.core.shop.skin;

import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public interface ShopGunSkin {
    Supplier<ItemStack> getSkin();
}
