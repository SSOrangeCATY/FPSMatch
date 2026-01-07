package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.common.capability.team.ShopCapability;
import com.phasetranscrystal.fpsmatch.common.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.common.item.tool.EditMode;
import com.phasetranscrystal.fpsmatch.common.item.tool.EditToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.handler.ClickActionContext;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ShopEditTool extends EditToolItem {
    public ShopEditTool(Properties pProperties) {
        super(pProperties);
    }

    /**
     * 获取当前工具选择的商店
     */
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
        if (selectedShopName.isEmpty()) {
            return Optional.empty();
        }

        return map.get().getMapTeams().getTeamByName(selectedShopName)
                .flatMap(team -> team.getCapabilityMap().get(ShopCapability.class))
                .flatMap(ShopCapability::getShopSafe);
    }

    @Override
    public List<String> getTeamsByMap(String type, String mapName) {
        return getTeamsByMap(type, mapName,
                (team) -> team.getCapabilityMap().get(ShopCapability.class)
                        .map(ShopCapability::isInitialized)
                        .orElse(false));
    }

    @Override
    public void handleLeftClick(ClickActionContext context) {
        if (context.isDoubleClicked()) {
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

    @Override
    public void handleRightClick(ClickActionContext context) {
        if (context.isShiftKeyDown()) {
            EditMode currentMode = context.tool().getCurrentEditMode(context.stack());
            modifyCurrentModeContent(context.tool(), context.stack(), context.player(), currentMode);
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

    @Override
    public void modifyCurrentModeContent(EditToolItem editTool, ItemStack stack, ServerPlayer player, EditMode currentMode) {
        super.modifyCurrentModeContent(editTool, stack, player, currentMode);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, @NotNull InteractionHand pUsedHand) {
        ItemStack itemInHand = pPlayer.getItemInHand(pUsedHand);

        if (pLevel.isClientSide()) {
            return InteractionResultHolder.success(itemInHand);
        }

        ServerPlayer serverPlayer = (ServerPlayer) pPlayer;
        ShopEditTool editTool = (ShopEditTool) itemInHand.getItem();

        if (!serverPlayer.isShiftKeyDown()) {
            String missingMsg = getMissingTagMessage(itemInHand);
            if (!missingMsg.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.literal(missingMsg).withStyle(ChatFormatting.RED));
                return InteractionResultHolder.success(itemInHand);
            }

            // 打开商店编辑器界面
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new EditorShopContainer(windowId, inv, itemInHand),
                            Component.translatable("gui.fpsm.shop_editor.title")
                    ),
                    buf -> buf.writeItem(itemInHand)
            );
        }

        return InteractionResultHolder.success(itemInHand);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return super.initCapabilities(stack, nbt);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack pStack, @Nullable Level pLevel,
                                @NotNull List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);

        // 添加商店编辑工具特有的提示
        pTooltipComponents.add(Component.literal(""));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.switch.mode").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.clear").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.switch_map").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.open_editor").withStyle(ChatFormatting.YELLOW));
    }

    /**
     * 获取缺失标签的错误信息
     */
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
}