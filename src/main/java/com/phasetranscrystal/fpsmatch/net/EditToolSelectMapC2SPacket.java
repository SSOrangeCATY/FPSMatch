package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.item.ShopEditTool;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class EditToolSelectMapC2SPacket {

    public EditToolSelectMapC2SPacket() {}
    public static void encode(EditToolSelectMapC2SPacket msg, FriendlyByteBuf buf) {}

    public static EditToolSelectMapC2SPacket decode(FriendlyByteBuf buf) {
        return new EditToolSelectMapC2SPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                handleSelectMap(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleSelectMap(ServerPlayer player) {
        String preSelectedMap, newMap;

        if (player.getMainHandItem().getItem() instanceof ShopEditTool iteractItem) {
            List<String> mapList = FPSMCore.getInstance().getMapNames();
            if (!mapList.isEmpty() && player.getMainHandItem().getOrCreateTag().contains(ShopEditTool.MAP_TAG)) {
                preSelectedMap = iteractItem.getTag(player.getMainHandItem(), ShopEditTool.MAP_TAG);
                int preIndex = mapList.indexOf(preSelectedMap);
                if (preIndex == mapList.size() - 1)
                    newMap = mapList.get(0);
                else newMap = mapList.get(preIndex + 1);
                iteractItem.setTag(player.getMainHandItem(), ShopEditTool.MAP_TAG, newMap);
            } else {
                if (mapList.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_map").withStyle(ChatFormatting.RED));
                    return;
                }
                newMap = mapList.get(0);
                iteractItem.setTag(player.getMainHandItem(), ShopEditTool.MAP_TAG, newMap);
            }
            player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.all_maps").withStyle(ChatFormatting.BOLD)
                    .append(mapList.toString()).withStyle(ChatFormatting.GREEN)
            );
            player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.select_map").withStyle(ChatFormatting.BOLD)
                    .append(newMap).withStyle(ChatFormatting.AQUA)
            );
        }
    }
}
