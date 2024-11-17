package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record BombActionC2SPacket(int action) {

    public static void encode(BombActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.action);
    }

    public static BombActionC2SPacket decode(FriendlyByteBuf buf) {
        return new BombActionC2SPacket(
                buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer serverPlayer = ctx.get().getSender();
        BaseMap map = FPSMCore.getMapByPlayer(serverPlayer);
        if (map == null || serverPlayer == null) {
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombActionS2CPacket(2));
            ctx.get().setPacketHandled(true);
            return;
        }
        BaseTeam team = map.getMapTeams().getTeamByPlayer(serverPlayer);
        if (team == null) {
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombActionS2CPacket(2));
            ctx.get().setPacketHandled(true);
            return;
        }

        ctx.get().enqueueWork(() -> {
            if (!map.checkCanPlacingBombs(team.getName())) {
                map.setDemolitionStates(action);
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombActionS2CPacket(action));
            } else {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombActionS2CPacket(2));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
