package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.mapselect.MapRoomActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MapRoomSettingsC2SPacket(String gameType, String mapName, String settingName, String value) {
    private static final int ID_MAX_LENGTH = 128;
    private static final int VALUE_MAX_LENGTH = 1024;

    public static void encode(MapRoomSettingsC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.gameType(), ID_MAX_LENGTH);
        buf.writeUtf(packet.mapName(), ID_MAX_LENGTH);
        buf.writeUtf(packet.settingName(), ID_MAX_LENGTH);
        buf.writeUtf(packet.value(), VALUE_MAX_LENGTH);
    }

    public static MapRoomSettingsC2SPacket decode(FriendlyByteBuf buf) {
        return new MapRoomSettingsC2SPacket(buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH), buf.readUtf(VALUE_MAX_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            MapRoomActionService.Result result = MapRoomActionService.setSetting(player, gameType, mapName, settingName, value);
            MapRoomActionService.sendMessage(player, result);
            result.detail().ifPresentOrElse(
                    detail -> FPSMatch.sendToPlayer(player, new MapRoomDetailS2CPacket(detail)),
                    () -> FPSMatch.sendToPlayer(player, new MapRoomToastS2CPacket(result.message(), !result.success()))
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
