package com.phasetranscrystal.fpsmatch.compat.spectate.net;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packets for TACZ inspect sync while spectating.
 */
public final class SpectatorInspectPackets {
    private SpectatorInspectPackets() {
    }

    public static void register() {
        FPSMatch.registerPacket(C2SStartInspectPacket.class);
        FPSMatch.registerPacket(S2CWatchedPlayerInspectPacket.class);
    }

    public record C2SStartInspectPacket() {
        public static C2SStartInspectPacket decode(FriendlyByteBuf b) {
            return new C2SStartInspectPacket();
        }

        public static void encode(C2SStartInspectPacket p, FriendlyByteBuf b) {
        }

        public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                S2CWatchedPlayerInspectPacket pkt = new S2CWatchedPlayerInspectPacket(sp.getUUID());
                for (ServerPlayer pl : sp.level().getServer().getPlayerList().getPlayers()) {
                    FPSMatch.sendToPlayer(pl, pkt);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record S2CWatchedPlayerInspectPacket(UUID id) {
        public static S2CWatchedPlayerInspectPacket decode(FriendlyByteBuf b) {
            return new S2CWatchedPlayerInspectPacket(b.readUUID());
        }

        public static void encode(S2CWatchedPlayerInspectPacket p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
        }

        public UUID getPlayerId() {
            return this.id;
        }

        public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
            ClientPacketExecutor.execute(ctx, this);
        }
    }

}
