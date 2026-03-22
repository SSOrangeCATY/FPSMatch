package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.CreatorToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickActionContext;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MapCreatorTool extends CreatorToolItem implements WorldToolItem {
    public static final String BLOCK_POS_TAG_1 = "BlockPos1";
    public static final String BLOCK_POS_TAG_2 = "BlockPos2";
    public static final String DRAFT_MAP_NAME_TAG = "DraftMapName";

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
                player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.set_pos1", formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            case RIGHT_CLICK_BLOCK -> {
                if (clickedPos == null) {
                    return;
                }
                setBlockPos(stack, BLOCK_POS_TAG_2, clickedPos);
                player.displayClientMessage(Component.translatable("message.fpsm.map_creator_tool.set_pos2", formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            case CTRL_RIGHT_CLICK -> FPSMatch.sendToPlayer(player,
                    OpenMapCreatorToolScreenS2CPacket.fromStack(stack, FPSMCore.getInstance().getGameTypes()));
        }
    }

    public static void setSelectedType(ItemStack stack, String selectedType) {
        stack.getOrCreateTag().putString(TYPE_TAG, selectedType == null ? "" : selectedType);
    }

    public static String getSelectedType(ItemStack stack) {
        return stack.getOrCreateTag().getString(TYPE_TAG);
    }

    public static void setDraftMapName(ItemStack stack, String draftMapName) {
        stack.getOrCreateTag().putString(DRAFT_MAP_NAME_TAG, draftMapName == null ? "" : draftMapName);
    }

    public static String getDraftMapName(ItemStack stack) {
        return stack.getOrCreateTag().getString(DRAFT_MAP_NAME_TAG);
    }

    public static void setBlockPos(ItemStack stack, String tag, @Nullable BlockPos pos) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (pos == null) {
            compoundTag.remove(tag);
            return;
        }
        compoundTag.putLong(tag, pos.asLong());
    }

    public static @Nullable BlockPos getBlockPos(ItemStack stack, String tag) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (!compoundTag.contains(tag, Tag.TAG_LONG)) {
            return null;
        }
        return BlockPos.of(compoundTag.getLong(tag));
    }

    public static String formatPos(@Nullable BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.selected.type")
                .append(": ")
                .append(Component.literal(getSelectedType(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedType(pStack)).withStyle(ChatFormatting.AQUA)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.selected.name")
                .append(": ")
                .append(Component.literal(getDraftMapName(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getDraftMapName(pStack)).withStyle(ChatFormatting.GREEN)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.selected.pos1")
                .append(": ")
                .append(Component.literal(formatPos(getBlockPos(pStack, BLOCK_POS_TAG_1))).withStyle(ChatFormatting.YELLOW)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.selected.pos2")
                .append(": ")
                .append(Component.literal(formatPos(getBlockPos(pStack, BLOCK_POS_TAG_2))).withStyle(ChatFormatting.YELLOW)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.left_click"));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.right_click"));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.map_creator.ctrl_right_click"));
    }
}
