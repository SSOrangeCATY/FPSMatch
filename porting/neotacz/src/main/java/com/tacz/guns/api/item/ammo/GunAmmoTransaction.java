package com.tacz.guns.api.item.ammo;

public record GunAmmoTransaction(boolean handled, int amount) {
    public GunAmmoTransaction {
        amount = Math.max(amount, 0);
    }

    public static GunAmmoTransaction notHandled() {
        return new GunAmmoTransaction(false, 0);
    }

    public static GunAmmoTransaction available(int amount) {
        return new GunAmmoTransaction(true, amount);
    }

    public static GunAmmoTransaction consumed(int amount) {
        return new GunAmmoTransaction(true, amount);
    }

    public static GunAmmoTransaction supplied(int amount) {
        return new GunAmmoTransaction(true, amount);
    }
}
