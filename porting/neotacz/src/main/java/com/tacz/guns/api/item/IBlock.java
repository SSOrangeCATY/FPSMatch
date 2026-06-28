package com.tacz.guns.api.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public interface IBlock {
    /**
     * 获取方块 ID
     *
     * @param block 输入物品
     * @return 方块 ID
     */
    Identifier getBlockId(ItemStack block);

    /**
     * 设置方块 ID
     */
    void setBlockId(ItemStack block, @Nullable Identifier blockId);
}
