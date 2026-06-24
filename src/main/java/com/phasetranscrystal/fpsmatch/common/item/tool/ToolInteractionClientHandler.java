package com.phasetranscrystal.fpsmatch.common.item.tool;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID, value = Dist.CLIENT)
public class ToolInteractionClientHandler {
    private static boolean isControlDown() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
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
        if (!level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        ToolInteractionAction action = isControlDown()
                ? ToolInteractionAction.CTRL_RIGHT_CLICK
                : ToolInteractionAction.RIGHT_CLICK_BLOCK;
        FPSMatch.sendToServer(new ToolInteractionC2SPacket(action, event.getPos()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !isControlDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        FPSMatch.sendToServer(new ToolInteractionC2SPacket(ToolInteractionAction.CTRL_RIGHT_CLICK, null));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!isControlDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WorldToolItem)) {
            return;
        }

        FPSMatch.sendToServer(new ToolInteractionC2SPacket(ToolInteractionAction.CTRL_RIGHT_CLICK, null));
    }
}
