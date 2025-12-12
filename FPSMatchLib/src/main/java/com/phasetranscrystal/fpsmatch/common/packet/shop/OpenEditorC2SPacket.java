package com.phasetranscrystal.fpsmatch.common.packet.shop;

import com.phasetranscrystal.fpsmatch.common.client.screen.EditorShopContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import java.util.function.Supplier;

public class OpenEditorC2SPacket {

    public OpenEditorC2SPacket() {
    }

    public static void encode(OpenEditorC2SPacket msg, FriendlyByteBuf buf) {
    }

    public static OpenEditorC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenEditorC2SPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                handleOpenEditor(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleOpenEditor(ServerPlayer player) {
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider(
                        (windowId, inv, p) -> new EditorShopContainer(windowId, inv, player.getMainHandItem()),
                        Component.translatable("gui.fpsm.shop_editor.title")
                ),
                buf -> buf.writeItem(player.getMainHandItem())  // 将物品写入缓冲区
        );
    }
}
