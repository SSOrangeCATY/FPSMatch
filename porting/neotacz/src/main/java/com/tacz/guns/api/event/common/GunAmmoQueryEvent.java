package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.ammo.GunAmmoRequest;
import net.neoforged.bus.api.Event;

public class GunAmmoQueryEvent extends Event implements KubeJSGunEventPoster<GunAmmoQueryEvent> {
    private final GunAmmoRequest request;
    private int availableAmount;

    public GunAmmoQueryEvent(GunAmmoRequest request, int availableAmount) {
        this.request = request;
        this.availableAmount = Math.max(availableAmount, 0);
        postEventToKubeJS(this);
    }

    public GunAmmoRequest getRequest() {
        return request;
    }

    public int getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(int availableAmount) {
        this.availableAmount = Math.max(availableAmount, 0);
    }
}
