package com.phasetranscrystal.fpsmatch.common.packet.shop;

import com.phasetranscrystal.fpsmatch.common.item.ShopEditTool;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EditToolSelectMapC2SPacket {

    private final boolean isShift;

    public EditToolSelectMapC2SPacket(boolean isShift) {
        this.isShift = isShift;
    }

    public static void encode(EditToolSelectMapC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isShift);
    }

    public static EditToolSelectMapC2SPacket decode(FriendlyByteBuf buf) {
        return new EditToolSelectMapC2SPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack item = player.getMainHandItem();
            if (!(item.getItem() instanceof ShopEditTool tool)) return;
            int clickCount = tool.getIntTag(item,ShopEditTool.DOUBLE_CLICK_COUNT_TAG);
            if(isShift) {
                if(clickCount >= 2){
                    tool.removeTag(item, ShopEditTool.TYPE_TAG);
                    tool.removeTag(item, ShopEditTool.MAP_TAG);
                    tool.removeTag(item, ShopEditTool.TEAM_TAG);
                    player.displayClientMessage(Component.translatable("message.fpsm.shop_edit_tool.clear").withStyle(ChatFormatting.DARK_AQUA),true);
                }
            }else{
                tool.switchEditMode(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}