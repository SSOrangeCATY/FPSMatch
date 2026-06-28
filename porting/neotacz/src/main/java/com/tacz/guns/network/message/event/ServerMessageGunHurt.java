package com.tacz.guns.network.message.event;

import com.tacz.guns.network.NetworkContext;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class ServerMessageGunHurt {
    private final int bulletId;
    private final int hurtEntityId;
    private final int attackerId;
    private final Identifier gunId;
    private final Identifier gunDisplayId;
    private final float amount;
    private final boolean isHeadShot;
    private final float headshotMultiplier;

    public ServerMessageGunHurt(int bulletId, int hurtEntityId, int attackerId, Identifier gunId, Identifier gunDisplayId,
                                float amount, boolean isHeadShot, float headshotMultiplier) {
        this.bulletId = bulletId;
        this.hurtEntityId = hurtEntityId;
        this.attackerId = attackerId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.amount = amount;
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
    }

    public static void encode(ServerMessageGunHurt message, FriendlyByteBuf buf) {
        buf.writeInt(message.bulletId);
        buf.writeInt(message.hurtEntityId);
        buf.writeInt(message.attackerId);
        buf.writeIdentifier(message.gunId);
        buf.writeIdentifier(message.gunDisplayId);
        buf.writeFloat(message.amount);
        buf.writeBoolean(message.isHeadShot);
        buf.writeFloat(message.headshotMultiplier);
    }

    public static ServerMessageGunHurt decode(FriendlyByteBuf buf) {
        int bulletId = buf.readInt();
        int hurtEntityId = buf.readInt();
        int attackerId = buf.readInt();
        Identifier gunId = buf.readIdentifier();
        Identifier gunDisplayId = buf.readIdentifier();
        float amount = buf.readFloat();
        boolean isHeadShot = buf.readBoolean();
        float headshotMultiplier = buf.readFloat();
        return new ServerMessageGunHurt(bulletId, hurtEntityId, attackerId, gunId, gunDisplayId, amount, isHeadShot, headshotMultiplier);
    }

    public static void handle(ServerMessageGunHurt message, Supplier<NetworkContext> contextSupplier) {
        NetworkHandler.handleClientboundMessage(message, contextSupplier);
    }

    public int getBulletId() {
        return bulletId;
    }

    public int getHurtEntityId() {
        return hurtEntityId;
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

    public float getAmount() {
        return amount;
    }

    public boolean isHeadShot() {
        return isHeadShot;
    }

    public float getHeadshotMultiplier() {
        return headshotMultiplier;
    }
}
