package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.capability.team.ShopCapability;
import com.phasetranscrystal.fpsmatch.common.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.common.packet.shop.EditToolSelectMapC2SPacket;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShopEditTool extends Item {
    public static final String TYPE_TAG = "SelectedType";
    public static final String MAP_TAG = "SelectedMap";
    public static final String TEAM_TAG = "SelectedShop";

    public static final String DOUBLE_CLICK_COUNT_TAG = "DoubleClickCount";
    public static final String DOUBLE_CLICK_LAST_TICK_TAG = "DoubleClickLastTick";

    public static final int DOUBLE_CLICK_TICK_LIMIT = 15;

    public static final String EDIT_MODE_TAG = "EditMode";

    public enum EditMode {
        TYPE,
        MAP,
        TEAM
    }

    public ShopEditTool(Properties pProperties) {
        super(pProperties);
    }

    public static Optional<FPSMShop<?>> getShop(ItemStack stack) {
        if (!(stack.getItem() instanceof ShopEditTool shopEditTool)) {
            return Optional.empty();
        }

        String selectedType = shopEditTool.getTag(stack, TYPE_TAG);
        String selectedMapName = shopEditTool.getTag(stack, MAP_TAG);
        if (selectedType.isEmpty() || selectedMapName.isEmpty()) {
            return Optional.empty();
        }

        Optional<BaseMap> map = FPSMCore.getInstance().getMapByTypeWithName(selectedType, selectedMapName);
        if (map.isEmpty()) {
            return Optional.empty();
        }

        String selectedShopName = shopEditTool.getTag(stack, TEAM_TAG);
        return map.get().getMapTeams().getTeamByName(selectedShopName)
                .flatMap(team -> team.getCapabilityMap().get(ShopCapability.class))
                .map(ShopCapability::getShopSafe).orElse(null);
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
        } catch (IllegalArgumentException e) {
            setEditMode(stack, EditMode.TYPE);
            return EditMode.TYPE;
        }
    }

    public void setEditMode(ItemStack stack, EditMode mode) {
        setTag(stack, EDIT_MODE_TAG, mode.name());
    }

    private List<String> getAvailableMapTypes() {
        return FPSMCore.getInstance().getGameTypes();
    }

    private List<String> getMapsByType(String mapType) {
        if (mapType.isEmpty()) {
            return new ArrayList<>();
        }
        return FPSMCore.getInstance().getMapNamesWithType(mapType);
    }

    private List<String> getTeamsByMap(String type, String mapName) {
        List<String> teamList = new ArrayList<>();
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByTypeWithName(type, mapName);
        map.ifPresent(baseMap -> baseMap.getMapTeams().getNormalTeams().forEach(team -> {
            team.getCapabilityMap().get(ShopCapability.class).ifPresent(cap -> {
                if (cap.isInitialized()) teamList.add(team.name);
            });
        }));
        return teamList;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if(level.isClientSide) return;

        if (!(stack.getItem() instanceof ShopEditTool editTool)) {
            return;
        }

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

        if(clickCount>0){
            lastClickTick++;
            setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, lastClickTick); // 回写NBT

            if(lastClickTick > DOUBLE_CLICK_TICK_LIMIT) {
                editTool.setIntTag(stack, DOUBLE_CLICK_COUNT_TAG, 0);
                editTool.setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, 0);
            }
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, @NotNull InteractionHand pUsedHand) {
        ItemStack itemInHand = pPlayer.getItemInHand(pUsedHand);
        if (!pLevel.isClientSide && pPlayer instanceof ServerPlayer serverPlayer) {
            ShopEditTool editTool = (ShopEditTool) itemInHand.getItem();
            EditMode currentMode = editTool.getCurrentEditMode(itemInHand);

            if (serverPlayer.isShiftKeyDown()) {
                modifyCurrentModeContent(editTool, itemInHand, serverPlayer, currentMode);
                return InteractionResultHolder.success(itemInHand);
            }

            String missingMsg = getMissingTagMessage(itemInHand);
            if (!missingMsg.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.literal(missingMsg).withStyle(ChatFormatting.RED));
                return InteractionResultHolder.success(itemInHand);
            }

            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new EditorShopContainer(windowId, inv, itemInHand),
                            Component.translatable("gui.fpsm.shop_editor.title")
                    ),
                    buf -> buf.writeItem(itemInHand)
            );
        }

        return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
    }

    private void modifyCurrentModeContent(ShopEditTool editTool, ItemStack stack, ServerPlayer player, EditMode currentMode) {
        switch (currentMode) {
            case TYPE:
                modifyType(editTool, stack, player);
                break;
            case MAP:
                String currentType = editTool.getTag(stack, TYPE_TAG);
                if (currentType.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_type").withStyle(ChatFormatting.RED));
                    return;
                }
                modifyMap(editTool, stack, player, currentType);
                break;
            case TEAM:
                String type = editTool.getTag(stack, TYPE_TAG);
                String map = editTool.getTag(stack, MAP_TAG);
                if (type.isEmpty() || map.isEmpty()) {
                    String msg = type.isEmpty() ? "message.fpsm.shop_edit_tool.missing_type" : "message.fpsm.shop_edit_tool.missing_map";
                    player.sendSystemMessage(Component.translatable(msg).withStyle(ChatFormatting.RED));
                    return;
                }
                modifyTeam(editTool, stack, player, type, map);
                break;
        }
    }

    private void modifyType(ShopEditTool editTool, ItemStack stack, ServerPlayer player) {
        List<String> typeList = getAvailableMapTypes();
        if (typeList.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_type").withStyle(ChatFormatting.RED));
            return;
        }

        String currentType = editTool.getTag(stack, TYPE_TAG);
        int currentIndex = typeList.indexOf(currentType);

        currentIndex = (currentIndex + 1) % typeList.size();
        String newType = typeList.get(currentIndex);

        editTool.setTag(stack, TYPE_TAG, newType);
        player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.switch_type", newType).withStyle(ChatFormatting.LIGHT_PURPLE));

        List<String> newMapList = getMapsByType(newType);
        String currentMap = editTool.getTag(stack, MAP_TAG);
        if (!newMapList.contains(currentMap) && !newMapList.isEmpty()) {
            editTool.setTag(stack, MAP_TAG, newMapList.get(0));
            player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.map_reset_after_type_switch", newMapList.get(0)).withStyle(ChatFormatting.YELLOW));
            editTool.removeTag(stack, TEAM_TAG);
        } else if (newMapList.isEmpty()) {
            editTool.removeTag(stack, MAP_TAG);
            editTool.removeTag(stack, TEAM_TAG);
        }
    }

    private void modifyMap(ShopEditTool editTool, ItemStack stack, ServerPlayer player, String currentType) {
        List<String> mapList = getMapsByType(currentType);
        if (mapList.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_map_by_type", currentType).withStyle(ChatFormatting.RED));
            return;
        }

        String currentMap = editTool.getTag(stack, MAP_TAG);
        int currentIndex = mapList.indexOf(currentMap);
        currentIndex = (currentIndex + 1) % mapList.size();
        String newMap = mapList.get(currentIndex);

        editTool.setTag(stack, MAP_TAG, newMap);
        player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.switch_map", newMap).withStyle(ChatFormatting.AQUA));

        editTool.removeTag(stack, TEAM_TAG);
    }

    private void modifyTeam(ShopEditTool editTool, ItemStack stack, ServerPlayer player, String type, String map) {
        List<String> teamList = getTeamsByMap(type, map);
        if (teamList.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_shop").withStyle(ChatFormatting.RED));
            return;
        }

        String currentTeam = editTool.getTag(stack, TEAM_TAG);
        int currentIndex = teamList.indexOf(currentTeam);
        currentIndex = (currentIndex == -1 || currentIndex + 1 >= teamList.size()) ? 0 : currentIndex + 1;
        String newTeam = teamList.get(currentIndex);

        editTool.setTag(stack, TEAM_TAG, newTeam);
        player.displayClientMessage(Component.translatable("message.fpsm.shop_edit_tool.switch_team", newTeam).withStyle(ChatFormatting.GREEN),true);
    }

    private String getMissingTagMessage(ItemStack stack) {
        ShopEditTool editTool = (ShopEditTool) stack.getItem();
        if (editTool.getTag(stack, TYPE_TAG).isEmpty()) {
            return Component.translatable("message.fpsm.shop_edit_tool.missing_type").getString();
        }
        if (editTool.getTag(stack, MAP_TAG).isEmpty()) {
            return Component.translatable("message.fpsm.shop_edit_tool.missing_map").getString();
        }
        if (editTool.getTag(stack, TEAM_TAG).isEmpty()) {
            return Component.translatable("message.fpsm.shop_edit_tool.missing_shop").getString();
        }
        return "";
    }

    // 辅助方法：获取玩家当前队伍（保留）
    private Optional<ServerTeam> getPlayerCurrentTeam(ServerPlayer player) {
        return FPSMCore.getInstance().getMapByPlayer(player).flatMap(map -> map.getMapTeams().getTeamByPlayer(player));
    }

    // 辅助方法：获取队伍所属地图（保留）
    private Optional<BaseMap> getTeamBelongingMap(ServerTeam team) {
        return FPSMCore.getInstance().getMapByTypeWithName(team.gameType,team.mapName);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide) return;
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof ShopEditTool)) return;
        FPSMatch.sendToServer(new EditToolSelectMapC2SPacket(player.isShiftKeyDown()));
    }

    public void switchEditMode(Player player) {
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof ShopEditTool editTool)) return;

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
            case TYPE -> Component.translatable("message.fpsm.shop_edit_tool.mode.type");
            case MAP -> Component.translatable("message.fpsm.shop_edit_tool.mode.map");
            case TEAM -> Component.translatable("message.fpsm.shop_edit_tool.mode.team");
        };
        player.displayClientMessage(Component.translatable("message.fpsm.shop_edit_tool.switch_mode", modeName).withStyle(ChatFormatting.DARK_AQUA),true);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new EditorShopCapabilityProvider(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack pStack, @Nullable Level pLevel, @NotNull List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        ShopEditTool editTool = (ShopEditTool) pStack.getItem();

        EditMode currentMode = editTool.getCurrentEditMode(pStack);
        MutableComponent modeName = switch (currentMode) {
            case TYPE -> Component.translatable("message.fpsm.shop_edit_tool.mode.type");
            case MAP -> Component.translatable("message.fpsm.shop_edit_tool.mode.map");
            case TEAM -> Component.translatable("message.fpsm.shop_edit_tool.mode.team");
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
                .append(Component.literal(selectedType.isEmpty() ? Component.translatable("tooltip.fpsm.none").getString() : selectedType)
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.selected.map")
                .append(": ")
                .append(Component.literal(selectedMap.isEmpty() ? Component.translatable("tooltip.fpsm.none").getString() : selectedMap)
                        .withStyle(ChatFormatting.AQUA)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.selected.shop")
                .append(": ")
                .append(Component.literal(selectedShop.isEmpty() ? Component.translatable("tooltip.fpsm.none").getString() : selectedShop)
                        .withStyle(ChatFormatting.GREEN)));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));

        pTooltipComponents.add(Component.translatable("tooltip.fpsm.usage").withStyle(ChatFormatting.GRAY));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.switch.mode").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.clear").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.switch_map").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.open_editor").withStyle(ChatFormatting.YELLOW));
    }
}