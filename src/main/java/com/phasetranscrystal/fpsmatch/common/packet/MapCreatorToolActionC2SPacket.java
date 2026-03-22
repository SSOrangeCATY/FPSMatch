package com.phasetranscrystal.fpsmatch.common.packet;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.util.PreviewColorUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

public record MapCreatorToolActionC2SPacket(
        Action action,
        String selectedType,
        String draftMapName,
        @Nullable BlockPos pos1,
        @Nullable BlockPos pos2
) {
    public enum Action {
        SAVE_DRAFT,
        PREVIEW,
        CREATE
    }

    public static void encode(MapCreatorToolActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action());
        buf.writeUtf(packet.selectedType());
        buf.writeUtf(packet.draftMapName());
        writeNullableBlockPos(buf, packet.pos1());
        writeNullableBlockPos(buf, packet.pos2());
    }

    public static MapCreatorToolActionC2SPacket decode(FriendlyByteBuf buf) {
        return new MapCreatorToolActionC2SPacket(
                buf.readEnum(Action.class),
                buf.readUtf(),
                buf.readUtf(),
                readNullableBlockPos(buf),
                readNullableBlockPos(buf)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof MapCreatorTool)) {
                return;
            }

            switch (action()) {
                case SAVE_DRAFT -> saveDraft(stack);
                case PREVIEW -> preview(player);
                case CREATE -> createMap(player, stack);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void saveDraft(ItemStack stack) {
        MapCreatorTool.setSelectedType(stack, selectedType().trim());
        MapCreatorTool.setDraftMapName(stack, draftMapName());
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, pos1());
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2, pos2());
    }

    private void preview(ServerPlayer player) {
        String type = selectedType().trim();
        if (!FPSMCore.getInstance().checkGameType(type)) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_type")
                    .append(Component.literal(" " + type)), false);
            return;
        }

        Optional<AreaData> areaData = createArea();
        if (areaData.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_area"), false);
            return;
        }

        String prefix = "map_preview:draft:" + player.getUUID() + ":";
        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(prefix));
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                prefix + type,
                Component.literal(draftMapName().trim().isEmpty() ? "Draft Map" : draftMapName().trim()),
                PreviewColorUtil.getMapPreviewColor(type),
                areaData.get()
        ));
    }

    private void createMap(ServerPlayer player, ItemStack stack) {
        String type = selectedType().trim();
        if (!FPSMCore.getInstance().checkGameType(type)) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_type"), false);
            return;
        }

        String mapName = draftMapName().trim();
        if (mapName.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_name"), false);
            return;
        }

        Optional<AreaData> areaData = createArea();
        if (areaData.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_area"), false);
            return;
        }

        if (FPSMCore.getInstance().isRegistered(type, mapName)) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.duplicate_map", mapName), false);
            return;
        }

        ServerLevel level = player.serverLevel();
        Function3<ServerLevel, String, AreaData, BaseMap> factory = FPSMCore.getInstance().getPreBuildGame(type);
        if (factory == null) {
            player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.invalid_type"), false);
            return;
        }

        BaseMap newMap = factory.apply(level, mapName, areaData.get());
        FPSMCore.getInstance().registerMap(type, newMap);

        MapCreatorTool.setSelectedType(stack, type);
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, pos1());
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2, pos2());
        MapCreatorTool.setDraftMapName(stack, "");

        player.displayClientMessage(Component.translatable("commands.fpsm.create.success", mapName), false);
        FPSMatch.sendToPlayer(player, OpenMapCreatorToolScreenS2CPacket.fromStack(stack, FPSMCore.getInstance().getGameTypes()));
    }

    private Optional<AreaData> createArea() {
        if (pos1() == null || pos2() == null) {
            return Optional.empty();
        }
        return Optional.of(new AreaData(pos1(), pos2()));
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
