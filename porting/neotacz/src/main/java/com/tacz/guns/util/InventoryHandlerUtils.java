package com.tacz.guns.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.EntityArmorInvWrapper;
import net.neoforged.neoforge.items.wrapper.EntityHandsInvWrapper;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;

import java.util.Optional;

public final class InventoryHandlerUtils {
    private InventoryHandlerUtils() {
    }

    public static Optional<IItemHandler> of(LivingEntity entity) {
        if (entity instanceof Player player) {
            return Optional.of(new PlayerInvWrapper(player.getInventory()));
        }
        return Optional.of(new CombinedInvWrapper(new EntityHandsInvWrapper(entity), new EntityArmorInvWrapper(entity)));
    }
}
