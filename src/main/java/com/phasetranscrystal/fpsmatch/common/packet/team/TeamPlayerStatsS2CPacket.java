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
    private final int totalKills;
    private final int totalDeaths;
    private final int totalAssists;
    private final float totalDamage;
    private final int mvpCount;
    private final boolean isLiving;
    private final int headshotKills;
    private final float healthPercent;
    private final boolean enableRounds;

    private final int tempKills;
    private final int tempDeaths;
    private final int tempAssists;
    private final float tempDamage;

    //  构建方法
    public static TeamPlayerStatsS2CPacket of(ServerTeam team, PlayerData data) {
        return new TeamPlayerStatsS2CPacket(
                data.getOwner(),
                team.name,
                data.name(),
                data.getScores(),
                data.getTotalKills(),
                data.getTotalDeaths(),
                data.getTotalAssists(),
                data.getTotalDamage(),
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
                                    int scores, int totalKills, int totalDeaths, int totalAssists,
                                    float totalDamage, int mvpCount, boolean isLiving, int headshotKills,
                                    float healthPercent, boolean enableRounds, int tempKills, int tempDeaths, int tempAssists, float tempDamage) {
        this.uuid = playerUuid;
        this.teamName = teamName;
        this.playerName = playerName;
        this.scores = scores;
        this.totalKills = totalKills;
        this.totalDeaths = totalDeaths;
        this.totalAssists = totalAssists;
        this.totalDamage = totalDamage;
        this.mvpCount = mvpCount;
        this.isLiving = isLiving;
        this.headshotKills = headshotKills;
        this.healthPercent = healthPercent;
        this.enableRounds = enableRounds;

        this.tempKills = tempKills;
        this.tempDeaths = tempDeaths;
        this.tempAssists = tempAssists;
        this.tempDamage = tempDamage;
    }

    public TeamPlayerStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.teamName = buf.readUtf();
        this.playerName = buf.readComponent();
        this.enableRounds = buf.readBoolean();

        this.scores = buf.readInt();
        this.totalKills = buf.readInt();
        this.totalDeaths = buf.readInt();
        this.totalAssists = buf.readInt();
        this.totalDamage = buf.readFloat();
        this.mvpCount = buf.readInt();
        this.isLiving = buf.readBoolean();
        this.headshotKills = buf.readInt();
        this.healthPercent = buf.readFloat();

        this.tempKills = buf.readInt();
        this.tempDeaths = buf.readInt();
        this.tempAssists = buf.readInt();
        this.tempDamage = buf.readFloat();
    }

    public static void encode(TeamPlayerStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeUtf(packet.teamName);
        buf.writeComponent(packet.playerName);
        buf.writeBoolean(packet.enableRounds);

        buf.writeInt(packet.scores);
        buf.writeInt(packet.totalKills);
        buf.writeInt(packet.totalDeaths);
        buf.writeInt(packet.totalAssists);
        buf.writeFloat(packet.totalDamage);
        buf.writeInt(packet.mvpCount);
        buf.writeBoolean(packet.isLiving);
        buf.writeInt(packet.headshotKills);
        buf.writeFloat(packet.healthPercent);

        buf.writeInt(packet.tempKills);
        buf.writeInt(packet.tempDeaths);
        buf.writeInt(packet.tempAssists);
        buf.writeFloat(packet.tempDamage);
    }

    public static TeamPlayerStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new TeamPlayerStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            FPSMClientGlobalData global = FPSMClient.getGlobalData();
            if (uuid.equals(mc.player.getUUID()) && !FPSMClient.getGlobalData().equalsTeam(teamName)) {
                global.setCurrentTeam(teamName);
            }

            Optional<PlayerData> opt = FPSMClient.getGlobalData().getPlayerTabData(teamName,uuid);

            opt.ifPresentOrElse(data->{
                data.setScores(scores);
                data.setTotalKills(totalKills);
                data.setTotalDeaths(totalDeaths);
                data.setTotalAssists(totalAssists);
                data.setTotalDamage(totalDamage);
                data.setMvpCount(mvpCount);
                data.setLiving(isLiving);
                data.setHeadshotKills(headshotKills);
                data.setHealthPercent(healthPercent);

                data.setTempAssists(tempAssists);
                data.setTempDamage(tempDamage);
                data.setTempKills(tempKills);
                data.setTempDeaths(tempDeaths);
            },()->{
                PlayerData clientData = new PlayerData(uuid, playerName, enableRounds);
                clientData.setScores(scores);
                clientData.setTotalKills(totalKills);
                clientData.setTotalDeaths(totalDeaths);
                clientData.setTotalAssists(totalAssists);
                clientData.setTotalDamage(totalDamage);
                clientData.setMvpCount(mvpCount);
                clientData.setLiving(isLiving);
                clientData.setHeadshotKills(headshotKills);
                clientData.setHealthPercent(healthPercent);

                clientData.setTempAssists(tempAssists);
                clientData.setTempDamage(tempDamage);
                clientData.setTempKills(tempKills);
                clientData.setTempDeaths(tempDeaths);
                FPSMClient.getGlobalData().setTabData(teamName, uuid, clientData);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getTeamName() {
        return teamName;
    }

    public Component getPlayerName() {
        return playerName;
    }

    public boolean isEnableRounds() {
        return enableRounds;
    }
}