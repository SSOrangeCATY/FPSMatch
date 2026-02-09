package com.phasetranscrystal.fpsmatch.compat.spectate.lrtactical;

import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import java.util.UUID;
import java.util.function.Supplier;
import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorView;
import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorLrtAttackPackets.S2CWatchedPlayerLrtAttackPacket;
import com.phasetranscrystal.fpsmatch.compat.spectate.tacz.SpectatorGunItemMirror;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.NetworkEvent;

/**
 * Handles spectator replication for LRTactical melee attacks.
 */
public final class SpectatorLrtAttackNet {
    private SpectatorLrtAttackNet() {
    }

    public static void handleWatchedPlayerAttackPacket(S2CWatchedPlayerLrtAttackPacket packet,
                                                       Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer localPlayer = mc.player;
            if (localPlayer == null) {
                return;
            }
            UUID targetId = packet.getPlayerId();
            if (localPlayer.getUUID().equals(targetId)) {
                return;
            }
            Player target = SpectatorView.getSpectatedPlayer(localPlayer);
            if (target == null || !target.getUUID().equals(targetId)) {
                return;
            }
            ItemStack targetStack = target.getMainHandItem();
            if (!targetStack.isEmpty()) {
                SpectatorGunItemMirror.equip(localPlayer, targetStack);
                SpectatorGunItemMirror.tick(localPlayer);
            }
            playAttackAnimation(localPlayer, packet.action());
        });
        ctx.setPacketHandled(true);
    }

    private static void playAttackAnimation(LocalPlayer localPlayer, MeleeAction action) {
        ItemStack stack = localPlayer.getMainHandItem();
        if (!(stack.getItem() instanceof IMeleeWeapon)) {
            return;
        }
        BlockEntityWithoutLevelRenderer renderer = IClientItemExtensions.of(stack).getCustomRenderer();
        if (renderer instanceof AnimateGeoItemRenderer animRenderer) {
            animRenderer.triggerAnimation(stack, action == MeleeAction.RIGHT ? "attack_right" : "attack_left");
        }
    }
}
