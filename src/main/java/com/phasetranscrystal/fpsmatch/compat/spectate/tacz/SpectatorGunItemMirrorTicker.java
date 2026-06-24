package com.phasetranscrystal.fpsmatch.compat.spectate.tacz;

import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Keeps the local held item mirrored to the spectated player's main hand.
 * Registered by {@link com.phasetranscrystal.fpsmatch.compat.tacz.TACZBootstrap} when TACZ is loaded.
 */
public final class SpectatorGunItemMirrorTicker {
    private SpectatorGunItemMirrorTicker() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null) {
            return;
        }
        Player target = SpectatorView.getSpectatedPlayer(self);
        if (target != null) {
            ItemStack targetStack = target.getMainHandItem();
            SpectatorGunItemMirror.equip(self, targetStack);
            SpectatorGunItemMirror.tick(self);
            return;
        }
        SpectatorGunItemMirror.revert(self);
    }
}
