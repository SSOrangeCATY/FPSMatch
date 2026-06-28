package com.tacz.guns.api.item.ammo;

public record GunAmmoSlotState(String slotId, int loadedAmmo, int reserveAmmo) {
    public GunAmmoSlotState {
        if (slotId == null || slotId.isBlank()) {
            throw new IllegalArgumentException("slotId must not be blank");
        }
        loadedAmmo = Math.max(loadedAmmo, 0);
        reserveAmmo = Math.max(reserveAmmo, 0);
    }
}
