package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record ToolInteractionC2SPacket(ToolInteractionAction action, @Nullable BlockPos clickedPos) {
    public static void encode(ToolInteractionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action());
        buf.writeBoolean(packet.clickedPos() != null);
        if (packet.clickedPos() != null) {
            buf.writeBlockPos(packet.clickedPos());
        }
    }

    public static ToolInteractionC2SPacket decode(FriendlyByteBuf buf) {
        ToolInteractionAction action = buf.readEnum(ToolInteractionAction.class);
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new ToolInteractionC2SPacket(action, pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof WorldToolItem worldToolItem) {
                worldToolItem.handleWorldInteraction(player, stack, action, clickedPos);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
