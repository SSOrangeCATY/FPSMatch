package com.phasetranscrystal.fpsmatch.compat.spectate.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorMotion;
import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Mirrors spectated player movement into TACZ gun animation states.
 * Registered by {@link com.phasetranscrystal.fpsmatch.compat.tacz.TACZBootstrap} when TACZ is loaded.
 */
public final class SpectatorGunMovementMirror {
    private SpectatorGunMovementMirror() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        Player target = SpectatorView.getSpectatedPlayer(self);
        if (target == null) {
            return;
        }
        ItemStack stack = SpectatorGunStacks.current(target);
        if (stack.isEmpty()) {
            return;
        }
        TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
            LuaAnimationStateMachine<GunAnimationStateContext> asm = display.getAnimationStateMachine();
            if (asm == null) {
                return;
            }
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
        });
    }
}
