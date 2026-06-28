package com.tacz.guns.client.renderer.item;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderers;
import com.tacz.guns.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class TaczItemRenderers {
    private static final GunItemRendererWrapper GUN_RENDERER = new GunItemRendererWrapper();
    private static final AmmoItemRenderer AMMO_RENDERER = new AmmoItemRenderer();
    private static final AttachmentItemRenderer ATTACHMENT_RENDERER = new AttachmentItemRenderer();
    private static final GunSmithTableItemRenderer GUN_SMITH_TABLE_RENDERER = new GunSmithTableItemRenderer();

    private static boolean firstPersonRegistered;

    private TaczItemRenderers() {
    }

    public static void registerFirstPersonRenderers() {
        if (firstPersonRegistered) {
            return;
        }
        IFPGeoItemRenderers.register(ModItems.MODERN_KINETIC_GUN.get(), GUN_RENDERER);
        firstPersonRegistered = true;
    }

    public static Optional<AnimateGeoItemRenderer<?, ?>> getAnimated(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        if (item == ModItems.MODERN_KINETIC_GUN.get()) {
            return Optional.of(GUN_RENDERER);
        }
        return Optional.empty();
    }

    public static GunItemRendererWrapper gun() {
        return GUN_RENDERER;
    }

    public static AmmoItemRenderer ammo() {
        return AMMO_RENDERER;
    }

    public static AttachmentItemRenderer attachment() {
        return ATTACHMENT_RENDERER;
    }

    public static GunSmithTableItemRenderer gunSmithTable() {
        return GUN_SMITH_TABLE_RENDERER;
    }
}
