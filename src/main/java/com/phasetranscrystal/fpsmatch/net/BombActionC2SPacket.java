package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public record BombActionC2SPacket(int action, UUID uuid) {

    public static void encode(BombActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.action);
        buf.writeUUID(packet.uuid);
    }

    public static BombActionC2SPacket decode(FriendlyByteBuf buf) {
        return new BombActionC2SPacket(
                buf.readInt(),buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(sender);
        if (map == null || sender == null) {
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new BombActionS2CPacket(2,uuid));
            ctx.get().setPacketHandled(true);
            return;
        }
        BaseTeam team = map.getMapTeams().getTeamByPlayer(sender);
        if (team == null) {
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new BombActionS2CPacket(2,uuid));
            ctx.get().setPacketHandled(true);
            return;
        }

        ctx.get().enqueueWork(() -> {
            if (map instanceof BlastModeMap<?> blastModeMap && !blastModeMap.checkCanPlacingBombs(team.getName())) {
                Entity entity = sender.serverLevel().getEntity(this.uuid);
                if(entity instanceof CompositionC4Entity c4){
                    Player player = c4.getDemolisher();
                    if(player != null){
                        if(!player.getUUID().equals(sender.getUUID()) ) {
                            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new BombActionS2CPacket(2, uuid));
                        }else{
                            if (action == 0){
                                c4.setDemolisher(null);
                            }
                            c4.setDemolitionStates(action);
                            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new BombActionS2CPacket(action,this.uuid));
                        }
                    }else{
                        c4.setDemolisher(sender);
                        if (action == 0){
                            c4.setDemolisher(null);
                        }
                        c4.setDemolitionStates(action);
                        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new BombActionS2CPacket(action,this.uuid));
                    }
                }else{
                    FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new BombActionS2CPacket(2,uuid));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
