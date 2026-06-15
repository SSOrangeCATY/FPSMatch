package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 队伍管理操作数据包，用于客户端请求移动玩家到指定队伍（需OP权限）
 */
public record TeamManageActionC2SPacket(String mapName, UUID targetPlayer, String targetTeam) {

    public static void encode(TeamManageActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.mapName());
        buf.writeUUID(packet.targetPlayer());
        buf.writeUtf(packet.targetTeam());
    }

    public static TeamManageActionC2SPacket decode(FriendlyByteBuf buf) {
        return new TeamManageActionC2SPacket(buf.readUtf(), buf.readUUID(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // 验证OP权限
            if (!sender.getServer().getPlayerList().isOp(sender.getGameProfile())) {
                FPSMatch.sendToPlayer(sender, new TeamManageResultS2CPacket(false, Component.translatable("gui.fpsm.team_manage.no_permission")));
                return;
            }

            BaseMap map = FPSMCore.getInstance().getMapByName(mapName).orElse(null);
            if (map == null) {
                FPSMatch.sendToPlayer(sender, new TeamManageResultS2CPacket(false, Component.translatable("gui.fpsm.team_manage.map_not_found")));
                return;
            }

            MapTeams mapTeams = map.getMapTeams();
            ServerPlayer target = sender.getServer().getPlayerList().getPlayer(targetPlayer);
            if (target == null) {
                FPSMatch.sendToPlayer(sender, new TeamManageResultS2CPacket(false, Component.translatable("gui.fpsm.team_manage.player_not_found")));
                return;
            }

            // 先让玩家离开当前队伍，再加入目标队伍
            mapTeams.leaveTeam(target);
            MapTeams.JoinTeamResult result = mapTeams.joinTeam(targetTeam, target);
            if (result.status() == MapTeams.JoinTeamResult.Status.JOINED) {
                mapTeams.broadcast();
                FPSMatch.sendToPlayer(sender, new TeamManageResultS2CPacket(true, Component.translatable("gui.fpsm.team_manage.move_success", target.getDisplayName().getString(), targetTeam)));
            } else {
                FPSMatch.sendToPlayer(sender, new TeamManageResultS2CPacket(false, Component.translatable("gui.fpsm.team_manage.move_failed", result.status().name())));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}