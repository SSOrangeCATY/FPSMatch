package com.phasetranscrystal.fpsmatch.core.shop.slot;

import net.minecraft.world.entity.player.Player;

import java.util.List;

public record ShopSlotChangeEvent(ShopSlot currentSlot, ShopSlot changedSlot, Player player, int flag) {

    /**
     * flag规范：>0表示购入，<0表示返回。数值对应数量。
     */
    @Override
    public int flag() {
        return flag;
    }
}
