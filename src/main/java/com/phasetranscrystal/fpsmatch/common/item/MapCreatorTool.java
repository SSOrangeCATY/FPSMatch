package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.CreatorToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickActionContext;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.util.PreviewColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class MapCreatorTool extends CreatorToolItem implements WorldToolItem {
    public static final String BLOCK_POS_TAG_1 = "BlockPos1";
    public static final String BLOCK_POS_TAG_2 = "BlockPos2";
    public static final String DRAFT_MAP_NAME_TAG = "DraftMapName";
    private static final String HELD_PREVIEW_STATE_TAG = "HeldMapCreatorPreviewState";
    private static final int HELD_PREVIEW_REFRESH_INTERVAL = 10;

    public MapCreatorTool(Properties pProperties) {
        super(pProperties.stacksTo(1));
    }

    @Override
    protected void onLeftClick(ClickActionContext context) {
    }

    @Override
    protected void onRightClick(ClickActionContext context) {
    }

    @Override
    public void handleWorldInteraction(ServerPlayer player, ItemStack stack, ToolInteractionAction action, @Nullable BlockPos clickedPos) {
        switch (action) {
            case LEFT_CLICK_BLOCK -> {
                if (clickedPos == null) {
                    return;
                }
                setBlockPos(stack, BLOCK_POS_TAG_1, clickedPos);
                player.sendSystemMessage(Component.translatable("message.fpsm.map_creator_tool.set_pos1", formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            case RIGHT_CLICK_BLOCK -> {
                if (clickedPos == null) {
                    return;
                }
                setBlockPos(stack, BLOCK_POS_TAG_2, clickedPos);
                player.sendSystemMessage(Component.translatable("message.fpsm.map_creator_tool.set_pos2", formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            case CTRL_RIGHT_CLICK -> FPSMatch.sendToPlayer(player,
                    OpenMapCreatorToolScreenS2CPacket.fromStack(stack, FPSMCore.getInstance().getGameTypes()));
        }
    }

    public void syncHeldPreview(ServerPlayer player, ItemStack stack) {
        BlockPos pos1 = getBlockPos(stack, BLOCK_POS_TAG_1);
        BlockPos pos2 = getBlockPos(stack, BLOCK_POS_TAG_2);
        if (pos1 == null || pos2 == null) {
            clearHeldPreview(player);
            return;
        }

        String selectedType = getSelectedType(stack).trim();
        String draftMapName = getDraftMapName(stack).trim();
        String signature = (selectedType.isBlank() ? "draft" : selectedType)
                + "|" + draftMapName
                + "|" + pos1.asLong()
                + "|" + pos2.asLong();
        String previousSignature = player.getPersistentData().getString(HELD_PREVIEW_STATE_TAG).orElse("");
        if (signature.equals(previousSignature) && player.tickCount % HELD_PREVIEW_REFRESH_INTERVAL != 0) {
            return;
        }

        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                getHeldPreviewKey(player),
                Component.literal(draftMapName.isEmpty() ? "Draft Map" : draftMapName),
                PreviewColorUtil.getMapPreviewColor(selectedType.isBlank() ? "draft" : selectedType),
                new AreaData(pos1, pos2)
        ));
        player.getPersistentData().putString(HELD_PREVIEW_STATE_TAG, signature);
    }

    public static void clearHeldPreview(ServerPlayer player) {
        if (!player.getPersistentData().contains(HELD_PREVIEW_STATE_TAG)) {
            return;
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        player.getPersistentData().remove(HELD_PREVIEW_STATE_TAG);
    }

    private static String getHeldPreviewPrefix(ServerPlayer player) {
        return "held_tool_preview:map_creator:" + player.getUUID() + ":";
    }

    private static String getHeldPreviewKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "area";
    }

    public static void setSelectedType(ItemStack stack, String selectedType) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TYPE_TAG, selectedType == null ? "" : selectedType));
    }

    public static String getSelectedType(ItemStack stack) {
        return customData(stack).getString(TYPE_TAG).orElse("");
    }

    public static void setDraftMapName(ItemStack stack, String draftMapName) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(DRAFT_MAP_NAME_TAG, draftMapName == null ? "" : draftMapName));
    }

    public static String getDraftMapName(ItemStack stack) {
        return customData(stack).getString(DRAFT_MAP_NAME_TAG).orElse("");
    }

    public static void setBlockPos(ItemStack stack, String tag, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, compoundTag -> {
            if (pos == null) {
                compoundTag.remove(tag);
            } else {
                compoundTag.putLong(tag, pos.asLong());
            }
        });
    }

    public static @Nullable BlockPos getBlockPos(ItemStack stack, String tag) {
        CompoundTag compoundTag = customData(stack);
        return compoundTag.getLong(tag).map(BlockPos::of).orElse(null);
    }

    public static String formatPos(@Nullable BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public void appendHoverText(ItemStack pStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, context, display, tooltip, pIsAdvanced);
        tooltip.accept(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.selected.type")
                .append(": ")
                .append(Component.literal(getSelectedType(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedType(pStack)).withStyle(ChatFormatting.AQUA)));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.selected.name")
                .append(": ")
                .append(Component.literal(getDraftMapName(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getDraftMapName(pStack)).withStyle(ChatFormatting.GREEN)));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.selected.pos1")
                .append(": ")
                .append(Component.literal(formatPos(getBlockPos(pStack, BLOCK_POS_TAG_1))).withStyle(ChatFormatting.YELLOW)));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.selected.pos2")
                .append(": ")
                .append(Component.literal(formatPos(getBlockPos(pStack, BLOCK_POS_TAG_2))).withStyle(ChatFormatting.YELLOW)));
        tooltip.accept(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.left_click"));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.right_click"));
        tooltip.accept(Component.translatable("tooltip.fpsm.map_creator.ctrl_right_click"));
    }
}
