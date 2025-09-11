package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class GameTabStatsS2CPacket {
    private final UUID uuid;
    private final PlayerData data;
    private final String team;

    public GameTabStatsS2CPacket(UUID uuid, PlayerData data, String team) {
        this.uuid = uuid;
        this.data = data;
        this.team = team;
    }

    public GameTabStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        Component name = buf.readComponent();
        int kills = buf.readInt();
        int _kills = buf.readInt();
        int deaths = buf.readInt();
        int _deaths = buf.readInt();
        int assists = buf.readInt();
        int _assists = buf.readInt();
        float damage = buf.readFloat();
        float _damage = buf.readFloat();
        int scores = buf.readInt();
        boolean isLiving = buf.readBoolean();
        int mvp = buf.readInt();
        float hp = buf.readFloat();
        PlayerData data = new PlayerData(this.uuid,name);
        data.setKills(kills);
        data.set_kills(_kills);
        data.setDeaths(deaths);
        data.set_deaths(_deaths);
        data.setAssists(assists);
        data.set_assists(_assists);
        data.setDamage(damage);
        data.set_damage(_damage);
        data.set_deaths(_deaths);
        data.set_assists(_assists);
        data.setScores(scores);
        data.setLiving(isLiving);
        data.setMvpCount(mvp);
        data.setHp(hp);
        this.data = data;
        this.team = buf.readUtf();
    }

    public static void encode(GameTabStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeComponent(packet.data.name());
        buf.writeInt(packet.data.getKills());
        buf.writeInt(packet.data._kills());
        buf.writeInt(packet.data.getDeaths());
        buf.writeInt(packet.data._deaths());
        buf.writeInt(packet.data.getAssists());
        buf.writeInt(packet.data._assists());
        buf.writeFloat(packet.data.getDamage());
        buf.writeFloat(packet.data._damage());
        buf.writeInt(packet.data.getScores());
        buf.writeBoolean(packet.data.isLiving());
        buf.writeInt(packet.data.getMvpCount());
        buf.writeFloat(packet.data.healthPercent());
        buf.writeUtf(packet.team);
    }

    public static GameTabStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new GameTabStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null && uuid.equals(Minecraft.getInstance().player.getUUID())) {
                if (!FPSMClient.getGlobalData().equalsTeam(team)) {
                    FPSMClient.getGlobalData().setCurrentTeam(team);
                }
            }
            FPSMClient.getGlobalData().setTabData(uuid,team,data);
        });
        ctx.get().setPacketHandled(true);
    }
}