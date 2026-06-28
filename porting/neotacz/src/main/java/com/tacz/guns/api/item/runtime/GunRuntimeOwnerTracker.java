package com.tacz.guns.api.item.runtime;

import com.tacz.guns.api.event.common.GunOwnerChangeEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.IdentityHashMap;
import java.util.Map;

public final class GunRuntimeOwnerTracker {
    private static final Map<ItemStack, LivingEntity> OWNERS = new IdentityHashMap<>();

    private GunRuntimeOwnerTracker() {
    }

    public static void observe(ServerPlayer player) {
        observe(player, player.getMainHandItem());
        observe(player, player.getOffhandItem());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            observe(player, player.getInventory().getItem(slot));
        }
    }

    public static void observe(LivingEntity entity, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IGun)) {
            return;
        }
        LivingEntity oldOwner = OWNERS.put(stack, entity);
        if (oldOwner != entity) {
            NeoForge.EVENT_BUS.post(new GunOwnerChangeEvent(stack, oldOwner, entity));
        }
    }
}
