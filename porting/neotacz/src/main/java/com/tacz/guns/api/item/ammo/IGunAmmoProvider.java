package com.tacz.guns.api.item.ammo;

@FunctionalInterface
public interface IGunAmmoProvider {
    GunAmmoTransaction handle(GunAmmoRequest request);
}
