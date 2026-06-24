package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public class FPSMSoundPlayC2SPacket {
    Identifier location;
    boolean playToTeam;

    public FPSMSoundPlayC2SPacket(Identifier location, boolean playToTeam) {
        this.location = location;
        this.playToTeam = playToTeam;
    }

    public static void encode(FPSMSoundPlayC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeIdentifier(packet.location);
        buf.writeBoolean(packet.playToTeam);
    }

    public static FPSMSoundPlayC2SPacket decode(FriendlyByteBuf buf) {
        return new FPSMSoundPlayC2SPacket(buf.readIdentifier(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ctx.get().enqueueWork(()-> {
            ServerPlayer player = ctx.get().getSender();
            if(player == null) return;
            FPSMSoundPlayS2CPacket packet = new FPSMSoundPlayS2CPacket(location);
            FPSMCore.getInstance().getMapByPlayer(player).ifPresentOrElse(map -> {
                if (playToTeam) {
                    map.getMapTeams().getTeamByPlayer(player).ifPresent(team -> {
                        map.sendPacketToTeamPlayer(team,packet,false);
                    });
                }else{
                    map.sendPacketToAllPlayer(packet);
                }
            },()->{
                MinecraftServer server = player.level().getServer();
                if(server == null) return;
                server.getPlayerList().getPlayers().forEach(
                        p->{
                            FPSMatch.sendToPlayer(p,packet);
                        }
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
