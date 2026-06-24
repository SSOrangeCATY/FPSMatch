package com.phasetranscrystal.fpsmatch.common.item.tool;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickActionContext;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.EditToolClickHandler;
import com.phasetranscrystal.fpsmatch.common.packet.EditToolClickC2SPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public abstract class FPSMToolItem extends Item implements EditToolClickHandler {
    public static final String TYPE_TAG = "SelectedType";
    public static final String MAP_TAG = "SelectedMap";
    public static final String TEAM_TAG = "SelectedTeam";
    public static final String EDIT_MODE_TAG = "EditMode";

    public static final String DOUBLE_CLICK_COUNT_TAG = "DoubleClickCount";
    public static final String DOUBLE_CLICK_LAST_TICK_TAG = "DoubleClickLastTick";
    public static final int DOUBLE_CLICK_TICK_LIMIT = 15;

    public FPSMToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public void handleClick(ItemStack stack, ServerPlayer player,
                            boolean isDoubleClicked, boolean isShiftKeyDown, ClickAction action) {
        ClickActionContext context = new ClickActionContext(stack, player,
                isDoubleClicked, isShiftKeyDown, action);

        switch (action) {
            case LEFT_CLICK -> onLeftClick(context);
            case RIGHT_CLICK -> onRightClick(context);
        }
    }

    @Override
    public final @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand interactionHand) {
        if (level.isClientSide()) return InteractionResult.PASS;
        this.handleClick(player.getItemInHand(interactionHand), (ServerPlayer) player, false, player.isShiftKeyDown(), ClickAction.RIGHT_CLICK);
        return InteractionResult.SUCCESS_SERVER;
    }

    protected abstract void onLeftClick(ClickActionContext context);

    protected abstract void onRightClick(ClickActionContext context);

    public void setTag(ItemStack stack, String tagName, String value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(tagName, value));
    }

    public String getTag(ItemStack stack, String tagName) {
        CompoundTag tag = customData(stack);
        return tag.getString(tagName).orElse("");
    }

    public int getIntTag(ItemStack stack, String tagName) {
        CompoundTag tag = customData(stack);
        return tag.getInt(tagName).orElse(0);
    }

    public void setIntTag(ItemStack stack, String tagName, int value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(tagName, value));
    }

    public void removeTag(ItemStack stack, String tagName) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(tagName));
    }

    protected static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public List<String> getAvailableMapTypes() {
        return FPSMCore.getInstance().getGameTypes();
    }

    public List<String> getMapsByType(String mapType) {
        if (mapType.isEmpty()) {
            return List.of();
        }
        return FPSMCore.getInstance().getMapNamesWithType(mapType);
    }

    public Optional<ServerTeam> getPlayerCurrentTeam(ServerPlayer player) {
        return FPSMCore.getInstance().getMapByPlayer(player).flatMap(map -> map.getMapTeams().getTeamByPlayer(player));
    }

    public Optional<BaseMap> getTeamBelongingMap(ServerTeam team) {
        return FPSMCore.getInstance().getMapByTypeWithName(team.gameType, team.mapName);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull ServerLevel level, @NotNull Entity entity,
                              @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);

        CompoundTag tag = customData(stack);
        if (!tag.contains(DOUBLE_CLICK_COUNT_TAG)) {
            this.setIntTag(stack, DOUBLE_CLICK_COUNT_TAG, 0);
        }
        if (!tag.contains(DOUBLE_CLICK_LAST_TICK_TAG)) {
            this.setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, 0);
        }

        int lastClickTick = this.getIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG);
        int clickCount = this.getIntTag(stack, DOUBLE_CLICK_COUNT_TAG);

        if (clickCount > 0) {
            lastClickTick++;
            this.setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, lastClickTick);

            if (lastClickTick > DOUBLE_CLICK_TICK_LIMIT) {
                this.setIntTag(stack, DOUBLE_CLICK_COUNT_TAG, 0);
                this.setIntTag(stack, DOUBLE_CLICK_LAST_TICK_TAG, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide()) return;
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof FPSMToolItem)) return;

        FPSMatch.sendToServer(new EditToolClickC2SPacket(
                ClickAction.LEFT_CLICK,
                player.isShiftKeyDown()
        ));
    }
}
