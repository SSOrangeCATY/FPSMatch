package com.tacz.guns.api.item.ammo;

import net.minecraft.resources.Identifier;

public record GunAmmoSlot(String slotId, Identifier ammoId, String ammoPoolId) {
    public GunAmmoSlot {
        if (slotId == null || slotId.isBlank()) {
            throw new IllegalArgumentException("slotId must not be blank");
        }
        if (ammoId == null) {
            throw new IllegalArgumentException("ammoId must not be null");
        }
        if (ammoPoolId == null || ammoPoolId.isBlank()) {
            ammoPoolId = slotId;
        }
    }
}
