package com.phasetranscrystal.fpsmatch.compat.spectate.lrtactical;

import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorMotion;
import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorView;
import me.xjqsh.lrtactical.client.renderer.item.FlashShieldItemRenderer;
import me.xjqsh.lrtactical.client.renderer.item.MeleeItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mirrors spectated player movement into LRTactical item animations.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = FPSMatch.MODID)
public final class SpectatorLrtMovementMirror {
    private SpectatorLrtMovementMirror() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        Player target = SpectatorView.getSpectatedPlayer(self);
        if (target == null) {
            return;
        }
        ItemStack stack = target.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        BlockEntityWithoutLevelRenderer renderer = IClientItemExtensions.of(stack).getCustomRenderer();
        if (renderer instanceof MeleeItemRenderer meleeRenderer) {
            LuaAnimationStateMachine<?> asm = meleeRenderer.getStateMachine(stack);
            if (asm != null) {
                tickMove(target, asm);
            }
            return;
        }
        if (renderer instanceof FlashShieldItemRenderer shieldRenderer) {
            LuaAnimationStateMachine<?> asm = shieldRenderer.getStateMachine(stack);
            if (asm != null) {
                tickMove(target, asm);
            }
        }
    }

    private static void tickMove(Player target, LuaAnimationStateMachine<?> asm) {
        if (target.isCrouching()) {
            asm.trigger("idle");
            return;
        }
        if (target.isSprinting()) {
            asm.trigger("run");
            return;
        }
        if (SpectatorMotion.isMoving(target)) {
            asm.trigger("walk");
        } else {
            asm.trigger("idle");
        }
    }
}
