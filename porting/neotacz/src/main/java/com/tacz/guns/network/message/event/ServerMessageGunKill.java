package com.tacz.guns.network.message.event;

import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class ServerMessageGunKill {
    private final int bulletId;
    private final int killEntityId;
    private final int attackerId;
    private final Identifier gunId;
    private final Identifier gunDisplayId;
    private final boolean isHeadShot;
    private final float baseDamage;
    private final float headshotMultiplier;

    public ServerMessageGunKill(int bulletId, int killEntityId, int attackerId, Identifier gunId, Identifier gunDisplayId, float baseDamage, boolean isHeadShot, float headshotMultiplier) {
        this.bulletId = bulletId;
        this.killEntityId = killEntityId;
        this.attackerId = attackerId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.baseDamage = baseDamage;
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
    }

    public static void encode(ServerMessageGunKill message, FriendlyByteBuf buf) {
        buf.writeInt(message.bulletId);
        buf.writeInt(message.killEntityId);
        buf.writeInt(message.attackerId);
        buf.writeIdentifier(message.gunId);
        buf.writeIdentifier(message.gunDisplayId);
        buf.writeFloat(message.baseDamage);
        buf.writeBoolean(message.isHeadShot);
        buf.writeFloat(message.headshotMultiplier);
    }

    public static ServerMessageGunKill decode(FriendlyByteBuf buf) {
        int bulletId = buf.readInt();
        int killEntityId = buf.readInt();
        int attackerId = buf.readInt();
        Identifier gunId = buf.readIdentifier();
        Identifier gunDisplayId = buf.readIdentifier();
        float baseDamage = buf.readFloat();
        boolean isHeadShot = buf.readBoolean();
        float headshotMultiplier = buf.readFloat();
        return new ServerMessageGunKill(bulletId, killEntityId, attackerId, gunId, gunDisplayId, baseDamage, isHeadShot, headshotMultiplier);
    }

    public static void handle(ServerMessageGunKill message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public int getBulletId() {
        return bulletId;
    }

    public int getKillEntityId() {
        return killEntityId;
    }

    public int getAttackerId() {
        return attackerId;
    }

    public Identifier getGunId() {
        return gunId;
    }

    public Identifier getGunDisplayId() {
        return gunDisplayId;
    }

    public boolean isHeadShot() {
        return isHeadShot;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public float getHeadshotMultiplier() {
        return headshotMultiplier;
    }
}
