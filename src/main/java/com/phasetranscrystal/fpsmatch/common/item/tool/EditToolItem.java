package com.phasetranscrystal.fpsmatch.common.item.tool;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickActionContext;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.EditToolClickHandler;
import com.phasetranscrystal.fpsmatch.common.packet.EditToolClickC2SPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public abstract class EditToolItem extends Item implements EditToolClickHandler {
    public static final String TYPE_TAG = "SelectedType";
    public static final String MAP_TAG = "SelectedMap";
    public static final String TEAM_TAG = "SelectedTeam";
    public static final String EDIT_MODE_TAG = "EditMode";

    public static final String DOUBLE_CLICK_COUNT_TAG = "DoubleClickCount";
    public static final String DOUBLE_CLICK_LAST_TICK_TAG = "DoubleClickLastTick";
    public static final int DOUBLE_CLICK_TICK_LIMIT = 15;

    public EditToolItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public final void handleClick(EditToolItem tool, ItemStack stack, ServerPlayer player,
                            boolean isDoubleClicked, boolean isShiftKeyDown, ClickAction action) {

        ClickActionContext context = new ClickActionContext(tool, stack, player,
                isDoubleClicked, isShiftKeyDown, action);

        switch (action) {
            case LEFT_CLICK:
                handleLeftClick(context);
                break;
            case RIGHT_CLICK:
                handleRightClick(context);
                break;
        }
    }

    protected void handleLeftClick(ClickActionContext context) {
        if(context.isDoubleClicked()){
            if (context.isShiftKeyDown()) {
                clearAllSelections(context);
            } else {
                EditMode currentMode = context.tool().getCurrentEditMode(context.stack());
                EditMode nextMode = getNextEditMode(currentMode);
                context.tool().setEditMode(context.stack(), nextMode);
                context.player().displayClientMessage(
                        Component.translatable("message.fpsm.edit_tool.switch_mode",
                                getModeName(nextMode)).withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
    }

    private EditMode getNextEditMode(EditMode currentMode) {
        return switch (currentMode) {
            case TYPE -> EditMode.MAP;
            case MAP -> EditMode.TEAM;
            case TEAM -> EditMode.TYPE;
        };
    }

    private Component getModeName(EditMode mode) {
        return switch (mode) {
            case TYPE -> Component.translatable("message.fpsm.edit_tool.mode.type");
            case MAP -> Component.translatable("message.fpsm.edit_tool.mode.map");
            case TEAM -> Component.translatable("message.fpsm.edit_tool.mode.team");
        };
    }

    protected void clearAllSelections(ClickActionContext context) {
        context.tool().removeTag(context.stack(), TYPE_TAG);
        context.tool().removeTag(context.stack(), MAP_TAG);
        context.tool().removeTag(context.stack(), TEAM_TAG);
        context.player().displayClientMessage(
                Component.translatable("message.fpsm.edit_tool.clear").withStyle(ChatFormatting.DARK_AQUA), true);
    }

    protected void handleRightClick(ClickActionContext context) {
        if(context.isShiftKeyDown()){
            EditMode currentMode = context.tool().getCurrentEditMode(context.stack());
            context.tool().modifyCurrentModeContent(context.tool(), context.stack(),
                    context.player(), currentMode);
        }
    }


    public void setTag(ItemStack stack, String tagName, String value) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(tagName, value);
    }

    public String getTag(ItemStack stack, String tagName) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains(tagName) ? tag.getString(tagName) : "";
    }

    public int getIntTag(ItemStack stack, String tagName) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains(tagName) ? tag.getInt(tagName) : 0;
    }

    public void setIntTag(ItemStack stack, String tagName, int value) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(tagName, value);
    }

    public void removeTag(ItemStack stack, String tagName){
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(tagName);
    }

    public EditMode getCurrentEditMode(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(EDIT_MODE_TAG)) {
            setEditMode(stack, EditMode.TYPE);
            return EditMode.TYPE;
        }
        try {
            return EditMode.valueOf(tag.getString(EDIT_MODE_TAG));
        } catch (Exception e) {
            setEditMode(stack, EditMode.TYPE);
            return EditMode.TYPE;
        }
    }

    public void setEditMode(ItemStack stack, EditMode mode) {
        setTag(stack, EDIT_MODE_TAG, mode.name());
    }

    public List<String> getAvailableMapTypes() {
        return FPSMCore.getInstance().getGameTypes();
    }

    public List<String> getMapsByType(String mapType) {
        if (mapType.isEmpty()) {
            return new ArrayList<>();
        }
        return FPSMCore.getInstance().getMapNamesWithType(mapType);
    }

    public abstract List<String> getTeamsByMap(String type, String mapName);

    public List<String> getTeamsByMap(String type, String mapName, Function<ServerTeam,Boolean> checker) {
        List<String> teamList = new ArrayList<>();
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByTypeWithName(type, mapName);
        map.ifPresent(baseMap -> baseMap.getMapTeams().getNormalTeams().forEach(team -> {
            if(checker.apply(team)) {
                teamList.add(team.name);
            }
        }));
        return teamList;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity,
                              int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if(level.isClientSide) return;

        if (!(stack.getItem() instanceof EditToolItem editTool)) {
            return;
        }

        // 双击检测逻辑
        if(!stack.getOrCreateTag().contains(DOUBLE_CLICK_COUNT_TAG)) {
            editTool.setIntTag(stack, DOUBLE_CLICK_COUNT_TAG, 0);
        }
        if(!stack.getOrCreateTag().contains(DOUBLE_CLICK_LAST_TICK_TAG)) {
            editTool.setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, 0);
        }

        if(!stack.getOrCreateTag().contains(EDIT_MODE_TAG)) {
            editTool.setEditMode(stack, EditMode.TYPE);
        }

        int lastClickTick = editTool.getIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG);
        int clickCount = editTool.getIntTag(stack, DOUBLE_CLICK_COUNT_TAG);

        if(clickCount > 0){
            lastClickTick++;
            setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, lastClickTick);

            if(lastClickTick > DOUBLE_CLICK_TICK_LIMIT) {
                editTool.setIntTag(stack, DOUBLE_CLICK_COUNT_TAG, 0);
                editTool.setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, 0);
            }
        }
    }

    public void modifyCurrentModeContent(EditToolItem editTool, ItemStack stack, ServerPlayer player, EditMode currentMode) {
        switch (currentMode) {
            case TYPE -> modifyType(editTool, stack, player);
            case MAP -> {
                String currentType = editTool.getTag(stack, TYPE_TAG);
                if (currentType.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.missing_type")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                modifyMap(editTool, stack, player, currentType);
            }
            case TEAM -> {
                String type = editTool.getTag(stack, TYPE_TAG);
                String map = editTool.getTag(stack, MAP_TAG);
                if (type.isEmpty() || map.isEmpty()) {
                    MutableComponent msg = type.isEmpty() ? Component.translatable("message.fpsm.edit_tool.missing_type")
                            : Component.translatable("message.fpsm.edit_tool.missing_map");
                    player.displayClientMessage(msg
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                modifyTeam(editTool, stack, player, type, map);
            }
        }
    }

    public void modifyType(EditToolItem editTool, ItemStack stack, ServerPlayer player) {
        List<String> typeList = getAvailableMapTypes();
        if (typeList.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.missing_type")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        String currentType = editTool.getTag(stack, TYPE_TAG);
        int currentIndex = typeList.indexOf(currentType);
        currentIndex = (currentIndex + 1) % typeList.size();
        String newType = typeList.get(currentIndex);

        editTool.setTag(stack, TYPE_TAG, newType);
        player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.switch_type", newType)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);

        List<String> newMapList = getMapsByType(newType);
        String currentMap = editTool.getTag(stack, MAP_TAG);
        if (!newMapList.contains(currentMap) && !newMapList.isEmpty()) {
            editTool.setTag(stack, MAP_TAG, newMapList.get(0));
            player.sendSystemMessage(Component.translatable("message.fpsm.edit_tool.map_reset_after_type_switch",
                    newMapList.get(0)).withStyle(ChatFormatting.YELLOW));
            editTool.removeTag(stack, TEAM_TAG);
        } else if (newMapList.isEmpty()) {
            editTool.removeTag(stack, MAP_TAG);
            editTool.removeTag(stack, TEAM_TAG);
        }
    }

    public void modifyMap(EditToolItem editTool, ItemStack stack, ServerPlayer player, String currentType) {
        List<String> mapList = getMapsByType(currentType);
        if (mapList.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.missing_map_by_type", currentType)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        String currentMap = editTool.getTag(stack, MAP_TAG);
        int currentIndex = mapList.indexOf(currentMap);
        currentIndex = (currentIndex + 1) % mapList.size();
        String newMap = mapList.get(currentIndex);

        editTool.setTag(stack, MAP_TAG, newMap);
        player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.switch_map", newMap)
                .withStyle(ChatFormatting.AQUA), true);

        editTool.removeTag(stack, TEAM_TAG);
    }

    public void modifyTeam(EditToolItem editTool, ItemStack stack, ServerPlayer player, String type, String map) {
        List<String> teamList = getTeamsByMap(type, map);
        if (teamList.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.missing_team")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        String currentTeam = editTool.getTag(stack, TEAM_TAG);
        int currentIndex = teamList.indexOf(currentTeam);
        currentIndex = (currentIndex == -1 || currentIndex + 1 >= teamList.size()) ? 0 : currentIndex + 1;
        String newTeam = teamList.get(currentIndex);

        editTool.setTag(stack, TEAM_TAG, newTeam);
        player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.switch_team", newTeam)
                .withStyle(ChatFormatting.GREEN), true);
    }

    public String getMissingTagMessage(ItemStack stack) {
        EditToolItem editTool = (EditToolItem) stack.getItem();
        if (editTool.getTag(stack, TYPE_TAG).isEmpty()) {
            return Component.translatable("message.fpsm.edit_tool.missing_type").getString();
        }
        if (editTool.getTag(stack, MAP_TAG).isEmpty()) {
            return Component.translatable("message.fpsm.edit_tool.missing_map").getString();
        }
        if (editTool.getTag(stack, TEAM_TAG).isEmpty()) {
            return Component.translatable("message.fpsm.edit_tool.missing_team").getString();
        }
        return "";
    }

    public void switchEditMode(Player player) {
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof EditToolItem editTool)) return;

        EditMode currentMode = editTool.getCurrentEditMode(stack);
        EditMode newMode = switch (currentMode) {
            case TYPE -> EditMode.MAP;
            case MAP -> EditMode.TEAM;
            case TEAM -> EditMode.TYPE;
        };

        String type = editTool.getTag(stack, TYPE_TAG);
        String map = editTool.getTag(stack, MAP_TAG);

        if (newMode == EditMode.MAP && type.isEmpty()) {
            newMode = EditMode.TYPE;
        }

        if (newMode == EditMode.TEAM && (type.isEmpty() || map.isEmpty())) {
            newMode = type.isEmpty() ? EditMode.TYPE : EditMode.MAP;
        }

        editTool.setEditMode(stack, newMode);
        Component modeName = switch (newMode) {
            case TYPE -> Component.translatable("message.fpsm.edit_tool.mode.type");
            case MAP -> Component.translatable("message.fpsm.edit_tool.mode.map");
            case TEAM -> Component.translatable("message.fpsm.edit_tool.mode.team");
        };
        player.displayClientMessage(Component.translatable("message.fpsm.edit_tool.switch_mode", modeName)
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }

    public Optional<ServerTeam> getPlayerCurrentTeam(ServerPlayer player) {
        return FPSMCore.getInstance().getMapByPlayer(player).flatMap(map -> map.getMapTeams().getTeamByPlayer(player));
    }

    public Optional<BaseMap> getTeamBelongingMap(ServerTeam team) {
        return FPSMCore.getInstance().getMapByTypeWithName(team.gameType, team.mapName);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack pStack, @Nullable Level pLevel,
                                @NotNull List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        EditToolItem editTool = (EditToolItem) pStack.getItem();

        EditMode currentMode = editTool.getCurrentEditMode(pStack);
        MutableComponent modeName = switch (currentMode) {
            case TYPE -> Component.translatable("message.fpsm.edit_tool.mode.type");
            case MAP -> Component.translatable("message.fpsm.edit_tool.mode.map");
            case TEAM -> Component.translatable("message.fpsm.edit_tool.mode.team");
        };
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.selected.mode")
                .append(": ")
                .append(modeName.withStyle(ChatFormatting.BLUE)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));

        String selectedType = getTag(pStack, TYPE_TAG);
        String selectedMap = getTag(pStack, MAP_TAG);
        String selectedShop = getTag(pStack, TEAM_TAG);

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.selected.type")
                .append(": ")
                .append(Component.literal(selectedType.isEmpty() ?
                                Component.translatable("tooltip.fpsm.none").getString() : selectedType)
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.selected.map")
                .append(": ")
                .append(Component.literal(selectedMap.isEmpty() ?
                                Component.translatable("tooltip.fpsm.none").getString() : selectedMap)
                        .withStyle(ChatFormatting.AQUA)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.selected.shop")
                .append(": ")
                .append(Component.literal(selectedShop.isEmpty() ?
                                Component.translatable("tooltip.fpsm.none").getString() : selectedShop)
                        .withStyle(ChatFormatting.GREEN)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.usage").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide) return;
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof EditToolItem)) return;

        FPSMatch.sendToServer(new EditToolClickC2SPacket(
                ClickAction.LEFT_CLICK,
                player.isShiftKeyDown()
        ));
    }
}