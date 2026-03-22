package com.phasetranscrystal.fpsmatch.common.item.tool;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ToolInteractionClientHandler {
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        FPSMatch.sendToServer(new ToolInteractionC2SPacket(ToolInteractionAction.LEFT_CLICK_BLOCK, event.getPos()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        ToolInteractionAction action = Screen.hasControlDown()
                ? ToolInteractionAction.CTRL_RIGHT_CLICK
                : ToolInteractionAction.RIGHT_CLICK_BLOCK;
        FPSMatch.sendToServer(new ToolInteractionC2SPacket(action, event.getPos()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!Screen.hasControlDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        FPSMatch.sendToServer(new ToolInteractionC2SPacket(ToolInteractionAction.CTRL_RIGHT_CLICK, null));
    }
}
