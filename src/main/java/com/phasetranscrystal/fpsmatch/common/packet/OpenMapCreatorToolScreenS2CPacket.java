package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.screen.MapCreatorToolScreen;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenMapCreatorToolScreenS2CPacket(
        List<String> availableTypes,
        String selectedType,
        String draftMapName,
        @Nullable BlockPos pos1,
        @Nullable BlockPos pos2
) {
    public static OpenMapCreatorToolScreenS2CPacket fromStack(ItemStack stack, List<String> availableTypes) {
        return new OpenMapCreatorToolScreenS2CPacket(
                List.copyOf(availableTypes),
                MapCreatorTool.getSelectedType(stack),
                MapCreatorTool.getDraftMapName(stack),
                MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1),
                MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2)
        );
    }

    public static void encode(OpenMapCreatorToolScreenS2CPacket packet, FriendlyByteBuf buf) {
        writeStringList(buf, packet.availableTypes());
        buf.writeUtf(packet.selectedType());
        buf.writeUtf(packet.draftMapName());
        writeNullableBlockPos(buf, packet.pos1());
        writeNullableBlockPos(buf, packet.pos2());
    }

    public static OpenMapCreatorToolScreenS2CPacket decode(FriendlyByteBuf buf) {
        return new OpenMapCreatorToolScreenS2CPacket(
                readStringList(buf),
                buf.readUtf(),
                buf.readUtf(),
                readNullableBlockPos(buf),
                readNullableBlockPos(buf)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof MapCreatorToolScreen screen) {
                screen.applyData(this);
            } else {
                minecraft.setScreen(new MapCreatorToolScreen(this));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    private static void writeNullableBlockPos(FriendlyByteBuf buf, @Nullable BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }

    private static @Nullable BlockPos readNullableBlockPos(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }
}
