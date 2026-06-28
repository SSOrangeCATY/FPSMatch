package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.ammo.GunAmmoRequest;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class GunAmmoSupplyEvent extends Event implements KubeJSGunEventPoster<GunAmmoSupplyEvent>, ICancellableEvent {
    public enum SourceType {
        AMMO_BOX,
        SPECIAL_AMMO_BOX,
        FULL_SUPPLY,
        CUSTOM
    }

    private final Player player;
    private final ItemStack sourceStack;
    private final SourceType sourceType;
    private final GunAmmoRequest request;
    private int suppliedAmount;

    public GunAmmoSupplyEvent(Player player, ItemStack sourceStack, SourceType sourceType, GunAmmoRequest request, int suppliedAmount) {
        this.player = player;
        this.sourceStack = sourceStack == null ? ItemStack.EMPTY : sourceStack;
        this.sourceType = sourceType;
        this.request = request;
        this.suppliedAmount = Math.max(suppliedAmount, 0);
        postEventToKubeJS(this);
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getSourceStack() {
        return sourceStack;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public GunAmmoRequest getRequest() {
        return request;
    }

    public int getSuppliedAmount() {
        return suppliedAmount;
    }

    public void setSuppliedAmount(int suppliedAmount) {
        this.suppliedAmount = Math.max(suppliedAmount, 0);
    }
}
