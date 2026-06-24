package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.mapselect.MapRoomQueryService;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record OpenMapSelectionC2SPacket() {
    public static void encode(OpenMapSelectionC2SPacket packet, FriendlyByteBuf buf) {
    }

    public static OpenMapSelectionC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenMapSelectionC2SPacket();
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            boolean viewerOp = FPSMUtil.hasPermissionLevel(player, 2);
            boolean nonOpButtonEnabled = FPSMConfig.Server.enableMapSelectionButtonForNonOps.get();
            if (!viewerOp && !nonOpButtonEnabled) {
                FPSMatch.sendToPlayer(player, new MapRoomToastS2CPacket(Component.translatable("gui.fpsm.map_select.action.no_permission"), true));
                return;
            }
            FPSMatch.sendToPlayer(player, new MapSelectionSnapshotS2CPacket(MapRoomQueryService.summaries(player), viewerOp, nonOpButtonEnabled));
        });
        ctx.get().setPacketHandled(true);
    }
}
