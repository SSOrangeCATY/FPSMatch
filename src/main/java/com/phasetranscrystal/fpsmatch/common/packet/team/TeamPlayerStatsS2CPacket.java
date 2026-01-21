package com.phasetranscrystal.fpsmatch.common.packet.team;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 玩家队伍数据同步包（服务端→客户端）
 * 核心逻辑：
 * 1. 根据enableRounds决定是否序列化回合字段
 * 2. 客户端仅解析聚合后的基础字段，不处理_开头字段
 */
public class TeamPlayerStatsS2CPacket {
    private final UUID uuid;
    private final String teamName;
    private final Component playerName;
    private final int scores;
    private final int Kills;
    private final int Deaths;
    private final int Assists;
    private final float Damage;
    private final int mvpCount;
    private final boolean isLiving;
    private final int headshotKills;
    private final float healthPercent;

    //  构建方法
    public static TeamPlayerStatsS2CPacket of(ServerTeam team, PlayerData data) {
        return new TeamPlayerStatsS2CPacket(
                data.getOwner(),
                team.name,
                data.name(),
                data.getScores(),
                data.getKills(),
                data.getDeaths(),
                data.getAssists(),
                data.getDamage(),
                data.getMvpCount(),
                data.isLivingServer(),
                data.getHeadshotKills(),
                data.healthPercentServer(),
                data.enableRounds,
                data.getTempKills(),
                data.getTempDeaths(),
                data.getTempAssists(),
                data.getTempDamage()
        );
    }

    public TeamPlayerStatsS2CPacket(UUID playerUuid, String teamName, Component playerName,
                                    int scores, int Kills, int Deaths, int Assists,
                                    float Damage, int mvpCount, boolean isLiving, int headshotKills,
                                    float healthPercent, boolean enableRounds, int tempKills, int tempDeaths, int tempAssists, float tempDamage) {
        this.uuid = playerUuid;
        this.teamName = teamName;
        this.playerName = playerName;
        this.scores = scores;
        this.Kills = Kills;
        this.Deaths = Deaths;
        this.Assists = Assists;
        this.Damage = Damage;
        this.mvpCount = mvpCount;
        this.isLiving = isLiving;
        this.headshotKills = headshotKills;
        this.healthPercent = healthPercent;
    }

    public TeamPlayerStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.teamName = buf.readUtf();
        this.playerName = buf.readComponent();

        this.scores = buf.readInt();
        this.Kills = buf.readInt();
        this.Deaths = buf.readInt();
        this.Assists = buf.readInt();
        this.Damage = buf.readFloat();
        this.mvpCount = buf.readInt();
        this.isLiving = buf.readBoolean();
        this.headshotKills = buf.readInt();
        this.healthPercent = buf.readFloat();
    }

    public static void encode(TeamPlayerStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeUtf(packet.teamName);
        buf.writeComponent(packet.playerName);

        buf.writeInt(packet.scores);
        buf.writeInt(packet.Kills);
        buf.writeInt(packet.Deaths);
        buf.writeInt(packet.Assists);
        buf.writeFloat(packet.Damage);
        buf.writeInt(packet.mvpCount);
        buf.writeBoolean(packet.isLiving);
        buf.writeInt(packet.headshotKills);
        buf.writeFloat(packet.healthPercent);
    }

    public static TeamPlayerStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new TeamPlayerStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            FPSMClientGlobalData global = FPSMClient.getGlobalData();
            if (uuid.equals(mc.player.getUUID()) && !FPSMClient.getGlobalData().isCurrentTeam(teamName)) {
                global.setCurrentTeam(teamName);
            }

            Optional<PlayerData> opt = FPSMClient.getGlobalData().getPlayerData(teamName,uuid);
            PlayerData data = opt.orElse(new PlayerData(uuid, playerName, false));
            data.setScores(scores);
            data.setKills(Kills);
            data.setDeaths(Deaths);
            data.setAssists(Assists);
            data.setDamage(Damage);
            data.setMvpCount(mvpCount);
            data.setLiving(isLiving);
            data.setHeadshotKills(headshotKills);
            data.setHealthPercent(healthPercent);
            FPSMClient.getGlobalData().updatePlayerTeamData(teamName, uuid, data);
        });
        ctx.get().setPacketHandled(true);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getTeamName() {
        return teamName;
    }

}