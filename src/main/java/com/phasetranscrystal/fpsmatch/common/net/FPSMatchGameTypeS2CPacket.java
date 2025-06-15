package com.phasetranscrystal.fpsmatch.common.net;

import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMatchGameTypeS2CPacket {
    private final String mapName;
    private final String gameType;

    public FPSMatchGameTypeS2CPacket(String mapName, String gameType) {
        this.mapName = mapName;
        this.gameType = gameType;
    }

    public static void encode(FPSMatchGameTypeS2CPacket packet, FriendlyByteBuf packetBuffer) {
        packetBuffer.writeUtf(packet.mapName);
        packetBuffer.writeUtf(packet.gameType);
    }

    public static FPSMatchGameTypeS2CPacket decode(FriendlyByteBuf packetBuffer) {
        return new FPSMatchGameTypeS2CPacket(
                packetBuffer.readUtf(),
                packetBuffer.readUtf()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientData.currentMap = this.mapName;
            ClientData.currentGameType = this.gameType;
        });
        context.setPacketHandled(true);
    }
} 