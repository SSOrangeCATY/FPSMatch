package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public class CSGameTabStatsPacket {
    private final UUID uuid;
    private final TabData tabData;

    public CSGameTabStatsPacket(UUID uuid, TabData tabData) {
        this.uuid = uuid;
        this.tabData = tabData;
    }

    public CSGameTabStatsPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.tabData = new TabData();
        this.tabData.setKills(buf.readInt());
        this.tabData.setDeaths(buf.readInt());
        this.tabData.setAssists(buf.readInt());
        this.tabData.setDamage(buf.readFloat());
        this.tabData.setMoney(buf.readInt());
    }

    public static void encode(CSGameTabStatsPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeInt(packet.tabData.getKills());
        buf.writeInt(packet.tabData.getDeaths());
        buf.writeInt(packet.tabData.getAssists());
        buf.writeFloat(packet.tabData.getDamage());
        buf.writeInt(packet.tabData.getMoney());
    }

    public static CSGameTabStatsPacket decode(FriendlyByteBuf buf) {
        return new CSGameTabStatsPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 这里可以获取 Minecraft 服务器上的 Player 对象
            // 假设有一个方法可以获取 Player 对象，例如 getPlayerByUUID
            // Player player = getPlayerByUUID(uuid);
            // 如果 player 不为空，我们可以更新他的 TabData
            // if (player != null) {
            //     TabData playerTabData = player.getTabData();
            //     playerTabData.setKills(tabData.getKills());
            //     playerTabData.setDeaths(tabData.getDeaths());
            //     playerTabData.setAssists(tabData.getAssists());
            //     playerTabData.setDamage(tabData.getDamage());
            //     playerTabData.setMoney(tabData.getMoney());
            // }
        });
        ctx.get().setPacketHandled(true);
    }
}