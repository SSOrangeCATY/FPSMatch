package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.effect.FPSMEffectRegister;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class DeathMessage {
    private final Component killer;
    private final UUID killerUUID;
    private final Component dead;
    private final UUID deadUUID;
    private final ItemStack weapon;
    private final String arg;
    private final boolean isHeadShot;
    private final boolean isBlinded;
    private final boolean isThroughSmoke;
    private final boolean isThroughWall;
    private final boolean isNoScope;
    
    private DeathMessage(Builder builder) {
        this.killer = builder.killer;
        this.killerUUID = builder.killerUUID;
        this.dead = builder.dead;
        this.deadUUID = builder.deadUUID;
        this.weapon = builder.weapon;
        this.arg = builder.arg;
        this.isHeadShot = builder.isHeadShot;
        this.isBlinded = builder.isBlinded;
        this.isThroughSmoke = builder.isThroughSmoke;
        this.isThroughWall = builder.isThroughWall;
        this.isNoScope = builder.isNoScope;
    }
    
    public static class Builder {
        private final Component killer;
        private final UUID killerUUID;
        private final Component dead;
        private final UUID deadUUID;
        private final ItemStack weapon;
        private String arg = "";
        private boolean isHeadShot = false;
        private boolean isBlinded = false;
        private boolean isThroughSmoke = false;
        private boolean isThroughWall = false;
        private boolean isNoScope = false;
        
        public Builder(Player killer, Player dead, ItemStack weapon) {
            this.killer = killer.getDisplayName();
            this.killerUUID = killer.getUUID();
            this.dead = dead.getDisplayName();
            this.deadUUID = dead.getUUID();
            this.weapon = weapon;
            this.isBlinded = killer.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS.get());
        }

        public Builder(Component killer, UUID killerUUID, Component dead, UUID deadUUID,ItemStack weapon) {
            this.killer = killer;
            this.killerUUID = killerUUID;
            this.dead = dead;
            this.deadUUID = deadUUID;
            this.weapon = weapon;
        }
        
        public Builder setArg(String arg) {
            this.arg = arg;
            return this;
        }
        
        public Builder setHeadShot(boolean headShot) {
            this.isHeadShot = headShot;
            return this;
        }
        
        public Builder setBlinded(boolean blinded) {
            this.isBlinded = blinded;
            return this;
        }
        
        public Builder setThroughSmoke(boolean throughSmoke) {
            this.isThroughSmoke = throughSmoke;
            return this;
        }
        
        public Builder setThroughWall(boolean throughWall) {
            this.isThroughWall = throughWall;
            return this;
        }
        
        public Builder setNoScope(boolean noScope) {
            this.isNoScope = noScope;
            return this;
        }
        
        public DeathMessage build() {
            return new DeathMessage(this);
        }
    }
    
    // Getters
    public Component getKiller() { return killer; }
    public UUID getKillerUUID() { return killerUUID; }
    public Component getDead() { return dead; }
    public UUID getDeadUUID() { return deadUUID; }
    public ItemStack getWeapon() { return weapon; }
    public String getArg() { return arg; }
    public boolean isHeadShot() { return isHeadShot; }
    public boolean isBlinded() { return isBlinded; }
    public boolean isThroughSmoke() { return isThroughSmoke; }
    public boolean isThroughWall() { return isThroughWall; }
    public boolean isNoScope() { return isNoScope; }
    
    public ResourceLocation getWeaponIcon() {
        Item item = weapon.getItem();
        if (item instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(weapon);
            ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gunId).orElse(null);
            return gunIndex != null ? gunIndex.getDefaultDisplay().getHUDTexture() : null;
        }
        return null;
    }
}
