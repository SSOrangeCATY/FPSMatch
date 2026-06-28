package com.tacz.guns.client.tooltip;

import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ClientAmmoBoxTooltip implements ClientTooltipComponent {
    private final ItemStack ammo;
    private final Component count;
    private final Component ammoName;

    public ClientAmmoBoxTooltip(AmmoBoxTooltip tooltip) {
        this.ammo = tooltip.getAmmo();
        ItemStack ammoBox = tooltip.getAmmoBox();
        if (ammoBox.getItem() instanceof IAmmoBox box && box.isCreative(ammoBox)) {
            this.count = Component.literal("∞");
        } else {
            this.count = Component.translatable("tooltip.tacz.ammo_box.count", tooltip.getCount());
        }
        this.ammoName = this.ammo.getHoverName();
    }

    @Override
    public int getHeight(Font font) {
        return 28;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(font.width(ammoName), font.width(count)) + 22;
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
        drawText(graphics, font, pX, pY);
    }

    private void drawText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
        graphics.text(font, ammoName, pX + 20, pY + 4, 0xffaa00, false);
        graphics.text(font, count, pX + 20, pY + 15, 0x666666, false);
    }

    @Override
    public void extractImage(Font pFont, int pX, int pY, int width, int height, GuiGraphicsExtractor graphics) {
        TooltipIconRenderer.drawSlotIcon(graphics, ammo, pX, pY + 5);
    }
}
