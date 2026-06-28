package com.tacz.guns.client.input;

import com.tacz.guns.GunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.common.CommonHooks;

public final class TaczClientInteraction {
    private TaczClientInteraction() {
    }

    public static void startUseItem(Minecraft mc) {
        if (mc.gameMode == null || mc.player == null || mc.level == null || mc.gameMode.isDestroying() || mc.player.isHandsBusy()) {
            return;
        }
        if (mc.hitResult == null) {
            GunMod.LOGGER.warn("Null returned as 'hitResult', this shouldn't happen!");
        }

        for (InteractionHand hand : InteractionHand.values()) {
            var inputEvent = ClientHooks.onClickInput(1, mc.options.keyUse, hand);
            if (inputEvent.isCanceled()) {
                if (inputEvent.shouldSwingHand()) {
                    mc.player.swing(hand);
                }
                return;
            }

            ItemStack heldItem = mc.player.getItemInHand(hand);
            if (!heldItem.isItemEnabled(mc.level.enabledFeatures())) {
                return;
            }

            if (mc.hitResult != null) {
                switch (mc.hitResult.getType()) {
                    case ENTITY -> {
                        EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
                        Entity entity = entityHit.getEntity();
                        if (!mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                            return;
                        }
                        if (mc.player.isWithinEntityInteractionRange(entity, 0.0)
                                && mc.gameMode.interact(mc.player, entity, entityHit, hand) instanceof InteractionResult.Success success) {
                            swingIfClient(mc, hand, inputEvent.shouldSwingHand(), success);
                            return;
                        }
                    }
                    case BLOCK -> {
                        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                        int oldCount = heldItem.getCount();
                        InteractionResult useResult = mc.gameMode.useItemOn(mc.player, hand, blockHit);
                        if (useResult instanceof InteractionResult.Success success) {
                            if (success.swingSource() == InteractionResult.SwingSource.CLIENT && inputEvent.shouldSwingHand()) {
                                mc.player.swing(hand);
                                if (!heldItem.isEmpty() && (heldItem.getCount() != oldCount || mc.player.hasInfiniteMaterials())) {
                                    mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
                                }
                            }
                            return;
                        }
                        if (useResult instanceof InteractionResult.Fail) {
                            return;
                        }
                    }
                    default -> {
                    }
                }
            }

            if (heldItem.isEmpty() && (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS)) {
                CommonHooks.onEmptyClick(mc.player, hand);
            }

            if (!heldItem.isEmpty() && mc.gameMode.useItem(mc.player, hand) instanceof InteractionResult.Success success) {
                swingIfClient(mc, hand, inputEvent.shouldSwingHand(), success);
                mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
                return;
            }
        }
    }

    private static void swingIfClient(Minecraft mc, InteractionHand hand, boolean shouldSwingHand, InteractionResult.Success success) {
        if (success.swingSource() == InteractionResult.SwingSource.CLIENT && shouldSwingHand) {
            mc.player.swing(hand);
        }
    }
}
