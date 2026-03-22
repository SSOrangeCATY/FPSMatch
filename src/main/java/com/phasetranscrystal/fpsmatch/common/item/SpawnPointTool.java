package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.capability.team.SpawnPointCapability;
import com.phasetranscrystal.fpsmatch.common.item.tool.CreatorToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickActionContext;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class SpawnPointTool extends CreatorToolItem implements WorldToolItem {
    public SpawnPointTool(Properties pProperties) {
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
                if (clickedPos != null) {
                    addSpawnPoint(player, stack, clickedPos);
                }
            }
            case CTRL_RIGHT_CLICK -> SpawnPointToolActionC2SPacket.sendScreen(player, stack,
                    getSelectedType(stack), getSelectedMap(stack), getSelectedTeam(stack), 0);
            case RIGHT_CLICK_BLOCK -> {
            }
        }
    }

    private void addSpawnPoint(ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        String selectedType = getSelectedType(stack);
        String selectedMap = getSelectedMap(stack);
        String selectedTeam = getSelectedTeam(stack);
        if (selectedType.isBlank() || selectedMap.isBlank() || selectedTeam.isBlank()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.missing_selection"), false);
            return;
        }

        Optional<BaseMap> mapOptional = FPSMCore.getInstance().getMapByTypeWithName(selectedType, selectedMap);
        if (mapOptional.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", selectedMap), false);
            return;
        }

        BaseMap map = mapOptional.get();
        if (!map.getServerLevel().dimension().equals(player.serverLevel().dimension())) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.dimension_mismatch"), false);
            return;
        }
        if (!map.getMapArea().isBlockPosInArea(clickedPos)) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.outside_map"), false);
            return;
        }

        Optional<ServerTeam> teamOptional = map.getMapTeams().getTeamByName(selectedTeam).filter(ServerTeam::isNormal);
        if (teamOptional.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.team_not_found", selectedTeam), false);
            return;
        }

        SpawnPointCapability capability = teamOptional.get().getCapabilityMap().get(SpawnPointCapability.class).orElse(null);
        if (capability == null) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.missing_capability"), false);
            return;
        }

        SpawnPointData spawnPointData = new SpawnPointData(
                player.serverLevel().dimension(),
                clickedPos.above().getCenter(),
                player.getYRot(),
                player.getXRot()
        );
        if (capability.getSpawnPointsData().contains(spawnPointData)) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.duplicate"), false);
            return;
        }

        capability.addSpawnPointData(spawnPointData);
        if (map.isStart()) {
            capability.assignNextSpawnPoints();
        }

        player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.added",
                MapCreatorTool.formatPos(clickedPos.above())).withStyle(ChatFormatting.GREEN), true);
    }

    public static void setSelectedType(ItemStack stack, String selectedType) {
        stack.getOrCreateTag().putString(TYPE_TAG, selectedType == null ? "" : selectedType);
    }

    public static String getSelectedType(ItemStack stack) {
        return stack.getOrCreateTag().getString(TYPE_TAG);
    }

    public static void setSelectedMap(ItemStack stack, String selectedMap) {
        stack.getOrCreateTag().putString(MAP_TAG, selectedMap == null ? "" : selectedMap);
    }

    public static String getSelectedMap(ItemStack stack) {
        return stack.getOrCreateTag().getString(MAP_TAG);
    }

    public static void setSelectedTeam(ItemStack stack, String selectedTeam) {
        stack.getOrCreateTag().putString(TEAM_TAG, selectedTeam == null ? "" : selectedTeam);
    }

    public static String getSelectedTeam(ItemStack stack) {
        return stack.getOrCreateTag().getString(TEAM_TAG);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.type")
                .append(": ")
                .append(Component.literal(getSelectedType(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedType(pStack)).withStyle(ChatFormatting.AQUA)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.map")
                .append(": ")
                .append(Component.literal(getSelectedMap(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedMap(pStack)).withStyle(ChatFormatting.GREEN)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.team")
                .append(": ")
                .append(Component.literal(getSelectedTeam(pStack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedTeam(pStack)).withStyle(ChatFormatting.YELLOW)));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.spawn_point_tool.left_click"));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.spawn_point_tool.ctrl_right_click"));
    }
}
