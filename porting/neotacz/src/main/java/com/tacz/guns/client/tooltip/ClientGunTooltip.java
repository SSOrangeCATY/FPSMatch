package com.tacz.guns.client.tooltip;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.item.GunTooltipPart;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;

public class ClientGunTooltip implements ClientTooltipComponent {
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("0%");

    private final ItemStack gun;
    private final IGun iGun;
    private final CommonGunIndex gunIndex;
    private final @Nullable GunDisplayInstance display;
    private final ItemStack ammo;
    private Component ammoName = Component.empty();
    private MutableComponent ammoCountText = Component.empty();
    private int maxWidth;

    public ClientGunTooltip(GunTooltip tooltip) {
        this.gun = tooltip.getGun();
        this.iGun = tooltip.getIGun();
        Identifier ammoId = tooltip.getAmmoId();
        this.gunIndex = tooltip.getGunIndex();
        this.display = TimelessAPI.getGunDisplay(gun).orElse(null);
        this.ammo = AmmoItemBuilder.create().setId(ammoId).build();
        this.buildAmmoText();
    }

    @Override
    public int getHeight(Font font) {
        return shouldShow(GunTooltipPart.AMMO_INFO) ? 24 : 0;
    }

    @Override
    public int getWidth(Font font) {
        return shouldShow(GunTooltipPart.AMMO_INFO) ? this.maxWidth : 0;
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
        if (!shouldShow(GunTooltipPart.AMMO_INFO)) {
            return;
        }
        graphics.text(font, this.ammoName, pX + 20, pY + 4, 0xffaa00, false);
        graphics.text(font, this.ammoCountText, pX + 20, pY + 14, 0x777777, false);
    }

    @Override
    public void extractImage(Font pFont, int pX, int pY, int width, int height, GuiGraphicsExtractor guiGraphics) {
        if (shouldShow(GunTooltipPart.AMMO_INFO)) {
            TooltipIconRenderer.drawSlotIcon(guiGraphics, ammo, pX, pY + 4);
        }
    }

    private void buildAmmoText() {
        if (!shouldShow(GunTooltipPart.AMMO_INFO)) {
            this.maxWidth = 0;
            return;
        }
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        this.ammoName = ammo.getHoverName();

        int barrelBulletAmount = (iGun.hasBulletInBarrel(gun) && gunIndex.getGunData().getBolt() != Bolt.OPEN_BOLT) ? 1 : 0;
        int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(gun, gunIndex.getGunData()) + barrelBulletAmount;
        int currentAmmoCount = iGun.getCurrentAmmoCount(this.gun) + barrelBulletAmount;

        if (!iGun.useDummyAmmo(gun)) {
            if (display != null && display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
                this.ammoCountText = Component.literal(CURRENT_AMMO_FORMAT_PERCENT.format((float) currentAmmoCount / (maxAmmoCount == 0 ? 1f : maxAmmoCount)));
            } else {
                this.ammoCountText = Component.literal("%d/%d".formatted(currentAmmoCount, maxAmmoCount));
            }
        } else {
            int dummyAmmoAmount = iGun.getDummyAmmoAmount(gun);
            if (display != null && display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
                String percent = CURRENT_AMMO_FORMAT_PERCENT.format((float) currentAmmoCount / (maxAmmoCount == 0 ? 1f : maxAmmoCount));
                this.ammoCountText = Component.literal("%s (%d)".formatted(percent, dummyAmmoAmount));
            } else {
                this.ammoCountText = Component.literal("%d/%d (%d)".formatted(currentAmmoCount, maxAmmoCount, dummyAmmoAmount));
            }
        }
        if (iGun.useInventoryAmmo(gun)) {
            this.ammoCountText = Component.translatable("tooltip.tacz.gun.inventory_mode").withStyle(ChatFormatting.YELLOW);
        }
        this.maxWidth = Math.max(font.width(this.ammoName), font.width(this.ammoCountText)) + 22;
    }

    private boolean shouldShow(GunTooltipPart part) {
        return (GunTooltipPart.getHideFlags(this.gun) & part.getMask()) == 0;
    }
}
