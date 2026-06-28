package com.tacz.guns.client.tooltip;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class TooltipIconRenderer {
    private static final int ICON_SIZE = 16;

    private TooltipIconRenderer() {
    }

    static boolean drawSlotIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        Identifier texture = getSlotTexture(stack);
        if (texture == null) {
            return false;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        return true;
    }

    @Nullable
    private static Identifier getSlotTexture(ItemStack stack) {
        if (stack.getItem() instanceof IGun) {
            return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getSlotTexture).orElseGet(MissingTextureAtlasSprite::getLocation);
        }
        if (stack.getItem() instanceof IAmmo ammo) {
            return TimelessAPI.getClientAmmoIndex(ammo.getAmmoId(stack)).map(index -> index.getSlotTextureLocation()).orElseGet(MissingTextureAtlasSprite::getLocation);
        }
        if (stack.getItem() instanceof IAttachment attachment) {
            return TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).map(index -> index.getSlotTexture()).orElseGet(MissingTextureAtlasSprite::getLocation);
        }
        return null;
    }
}
