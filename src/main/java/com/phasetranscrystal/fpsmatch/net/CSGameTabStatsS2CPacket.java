package com.phasetranscrystal.fpsmatch.net;

import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class CSGameTabStatsS2CPacket {
    private final UUID uuid;
    private final int kills;
    private final int deaths;
    private final int assists;
    private final float damage;
    private final boolean isLiving;
    private final int headshotKills;
    private final String team;

    public CSGameTabStatsS2CPacket(UUID uuid, PlayerData data, String team) {
        this.uuid = uuid;
        this.kills = data.getKills();
        this.deaths = data.getDeaths();
        this.assists = data.getAssists();
        this.damage = data.getDamage();
        this.isLiving = data.isLiving();
        this.headshotKills = data.getHeadshotKills();
        this.team = team;
    }

    public CSGameTabStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.kills = buf.readInt();
        this.deaths = buf.readInt();
        this.assists = buf.readInt();
        this.damage = buf.readFloat();
        this.isLiving = buf.readBoolean();
        this.headshotKills = buf.readInt();
        this.team = buf.readUtf();
    }

    public static void encode(CSGameTabStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeInt(packet.kills);
        buf.writeInt(packet.deaths);
        buf.writeInt(packet.assists);
        buf.writeFloat(packet.damage);
        buf.writeBoolean(packet.isLiving);
        buf.writeInt(packet.headshotKills);
        buf.writeUtf(packet.team);
    }

    public static CSGameTabStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new CSGameTabStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null && uuid.equals(Minecraft.getInstance().player.getUUID())) {
                if (!ClientData.currentTeam.equals(team)) {
                    ClientData.currentTeam = team;
                    CSGameShopScreen.refreshFlag = true;
                }
            }
            ClientData.tabData.put(uuid,new Pair<>(team, new TabData(this.kills,this.deaths,this.assists,this.damage,this.isLiving,this.headshotKills)));
        });
        ctx.get().setPacketHandled(true);
    }
}