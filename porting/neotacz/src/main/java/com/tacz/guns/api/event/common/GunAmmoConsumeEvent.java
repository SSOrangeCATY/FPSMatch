package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.ammo.GunAmmoRequest;
import net.neoforged.bus.api.Event;

public class GunAmmoConsumeEvent extends Event implements KubeJSGunEventPoster<GunAmmoConsumeEvent> {
    private final GunAmmoRequest request;
    private int consumedAmount;

    public GunAmmoConsumeEvent(GunAmmoRequest request, int consumedAmount) {
        this.request = request;
        this.consumedAmount = Math.max(consumedAmount, 0);
        postEventToKubeJS(this);
    }

    public GunAmmoRequest getRequest() {
        return request;
    }

    public int getConsumedAmount() {
        return consumedAmount;
    }

    public void setConsumedAmount(int consumedAmount) {
        this.consumedAmount = Math.max(consumedAmount, 0);
    }
}
