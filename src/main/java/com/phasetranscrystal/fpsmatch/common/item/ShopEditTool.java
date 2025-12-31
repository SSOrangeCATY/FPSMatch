package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.capability.team.ShopCapability;
import com.phasetranscrystal.fpsmatch.common.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShopEditTool extends EditToolItem {
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

    @Override
    public List<String> getTeamsByMap(String type, String mapName) {
        return getTeamsByMap(type,mapName,(team)-> team.getCapabilityMap().get(ShopCapability.class).map(ShopCapability::isInitialized).orElse(false));
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

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new EditorShopCapabilityProvider(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack pStack, @Nullable Level pLevel, @NotNull List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.switch.mode").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.clear").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.switch_map").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.shop_edit_tool.open_editor").withStyle(ChatFormatting.YELLOW));
    }
}