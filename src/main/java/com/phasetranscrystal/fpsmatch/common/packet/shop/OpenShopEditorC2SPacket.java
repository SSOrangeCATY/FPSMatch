package com.phasetranscrystal.fpsmatch.common.packet.shop;

import com.phasetranscrystal.fpsmatch.common.capability.team.ShopCapability;
import com.phasetranscrystal.fpsmatch.common.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.common.mapselect.MapRoomQueryService;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.function.Supplier;

public record OpenShopEditorC2SPacket(String gameType, String mapName, String teamName) {
    private static final int ID_MAX_LENGTH = 128;

    public static void encode(OpenShopEditorC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.gameType(), ID_MAX_LENGTH);
        buf.writeUtf(packet.mapName(), ID_MAX_LENGTH);
        buf.writeUtf(packet.teamName(), ID_MAX_LENGTH);
    }

    public static OpenShopEditorC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenShopEditorC2SPacket(buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (!isValidId(gameType) || !isValidId(mapName) || !isValidId(teamName)) {
                player.sendSystemMessage(Component.translatable("message.fpsm.shop_editor.invalid"));
                return;
            }
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.fpsm.shop_editor.invalid"));
                return;
            }
            Optional<BaseMap> map = MapRoomQueryService.findMap(gameType, mapName);
            Optional<ServerTeam> team = map.flatMap(this::findTeam);
            if (team.flatMap(ShopCapability::getShop).isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.fpsm.shop_editor.invalid"));
                return;
            }
            NetworkHooks.openScreen(player,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new EditorShopContainer(windowId, inv, gameType, mapName, teamName),
                            Component.translatable("gui.fpsm.shop_editor.title")
                    ),
                    buf -> {
                        buf.writeUtf(gameType);
                        buf.writeUtf(mapName);
                        buf.writeUtf(teamName);
                    }
            );
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean isValidId(String value) {
        return value != null && !value.isBlank() && value.length() <= ID_MAX_LENGTH;
    }

    private Optional<ServerTeam> findTeam(BaseMap map) {
        return map.getMapTeams().getNormalTeams().stream()
                .filter(team -> team.getName().equals(teamName))
                .findFirst();
    }
}
