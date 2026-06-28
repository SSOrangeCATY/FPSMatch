package com.tacz.guns.api.item.ammo;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public record GunAmmoRequest(
        LivingEntity shooter,
        ItemStack gunItem,
        String ammoSlotId,
        Identifier ammoId,
        String ammoPoolId,
        int amount,
        Kind kind
) {
    public GunAmmoRequest {
        gunItem = gunItem == null ? ItemStack.EMPTY : gunItem;
        ammoSlotId = ammoSlotId == null || ammoSlotId.isBlank() ? "main" : ammoSlotId;
        if (ammoId == null) {
            throw new IllegalArgumentException("ammoId must not be null");
        }
        ammoPoolId = ammoPoolId == null || ammoPoolId.isBlank() ? ammoSlotId : ammoPoolId;
        amount = Math.max(amount, 0);
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }

    public enum Kind {
        QUERY,
        CONSUME,
        SUPPLY
    }
}
