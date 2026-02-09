package com.phasetranscrystal.fpsmatch.compat.spectate.net;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import com.phasetranscrystal.fpsmatch.compat.spectate.lrtactical.SpectatorLrtAttackNet;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Packets for LRTactical attack sync while spectating.
 */
public final class SpectatorLrtAttackPackets {
    private SpectatorLrtAttackPackets() {
    }

    public static void register(SimpleChannel channel, AtomicInteger id) {
        channel.messageBuilder(C2SLrtAttackPacket.class, id.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SLrtAttackPacket::decode)
                .encoder(C2SLrtAttackPacket::encode)
                .consumerMainThread((pkt, ctx) -> handleLrtAttackPacket(channel, pkt, ctx))
                .add();

        channel.messageBuilder(S2CWatchedPlayerLrtAttackPacket.class, id.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CWatchedPlayerLrtAttackPacket::decode)
                .encoder(S2CWatchedPlayerLrtAttackPacket::encode)
                .consumerMainThread(S2CWatchedPlayerLrtAttackPacket::handle)
                .add();
    }

    public record C2SLrtAttackPacket(MeleeAction action) {
        public static C2SLrtAttackPacket decode(FriendlyByteBuf b) {
            int ordinal = b.readInt();
            MeleeAction[] values = MeleeAction.values();
            if (ordinal < 0 || ordinal >= values.length) {
                return new C2SLrtAttackPacket(MeleeAction.LEFT);
            }
            return new C2SLrtAttackPacket(values[ordinal]);
        }

        public static void encode(C2SLrtAttackPacket p, FriendlyByteBuf b) {
            b.writeInt(p.action.ordinal());
        }
    }

    public record S2CWatchedPlayerLrtAttackPacket(UUID id, MeleeAction action) {
        public static S2CWatchedPlayerLrtAttackPacket decode(FriendlyByteBuf b) {
            UUID playerId = b.readUUID();
            int ordinal = b.readInt();
            MeleeAction[] values = MeleeAction.values();
            MeleeAction action = ordinal >= 0 && ordinal < values.length ? values[ordinal] : MeleeAction.LEFT;
            return new S2CWatchedPlayerLrtAttackPacket(playerId, action);
        }

        public static void encode(S2CWatchedPlayerLrtAttackPacket p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
            b.writeInt(p.action.ordinal());
        }

        public UUID getPlayerId() {
            return this.id;
        }

        public static void handle(S2CWatchedPlayerLrtAttackPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    SpectatorLrtAttackNet.handleWatchedPlayerAttackPacket(p, ctx);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void handleLrtAttackPacket(SimpleChannel channel, C2SLrtAttackPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            S2CWatchedPlayerLrtAttackPacket pkt = new S2CWatchedPlayerLrtAttackPacket(sp.getUUID(), m.action);
            for (ServerPlayer pl : sp.server.getPlayerList().getPlayers()) {
                channel.sendTo(pkt, pl.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
