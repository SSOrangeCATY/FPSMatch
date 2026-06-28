package com.tacz.guns.client.gui.components.refit;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class GunPropertyDiagrams {
    private static final int PANEL_WIDTH = 288;
    private static final int PANEL_PADDING = 5;

    public static int getHidePropertyButtonYOffset() {
        int[] startYOffset = new int[]{49};
        AttachmentPropertyManager.getModifiers().forEach((key, value) -> {
            startYOffset[0] += value.getDiagramsDataSize() * 10;
        });
        return startYOffset[0];
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        graphics.fill(x, y, x + PANEL_WIDTH, y + getHidePropertyButtonYOffset() - 11, 0xAF222222);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player).getCacheProperty();
        if (cacheProperty == null) {
            return;
        }
        Identifier gunId = iGun.getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            GunData gunData = index.getGunData();
            FireMode fireMode = iGun.getFireMode(gunItem);

            int barStartX = x + 83;
            int barMaxWidth = 120;
            int barEndX = barStartX + barMaxWidth;

            int barBackgroundColor = 0xFF000000;
            int barBaseColor = 0xFFFFFFFF;
            int barPositivelyColor = 0xFF_55FF55;
            int barNegativeColor = 0xFF_FF5555;

            int fontColor = 0xCCCCCC;
            int nameTextStartX = x + 5;
            int valueTextStartX = x + 210;
            int valueTextRightX = x + PANEL_WIDTH - PANEL_PADDING;

            int[] yOffset = new int[]{y + 5};

            // 射击模式
            MutableComponent fireModeText = Component.translatable("gui.tacz.gun_refit.property_diagrams.fire_mode");
            if (fireMode == FireMode.AUTO) {
                fireModeText.append(Component.translatable("gui.tacz.gun_refit.property_diagrams.auto"));
            } else if (fireMode == FireMode.SEMI) {
                fireModeText.append(Component.translatable("gui.tacz.gun_refit.property_diagrams.semi"));
            } else if (fireMode == FireMode.BURST) {
                fireModeText.append(Component.translatable("gui.tacz.gun_refit.property_diagrams.burst"));
            } else {
                fireModeText.append(Component.translatable("gui.tacz.gun_refit.property_diagrams.unknown"));
            }

            graphics.text(font, fireModeText, nameTextStartX+12, yOffset[0], fontColor, false);

            yOffset[0] += 10;


            // 弹匣容量
            if (iGun.useInventoryAmmo(gunItem)) {
                // 如果使用背包直读，则直接显示满条和 INV 的标注
                graphics.text(font, Component.translatable("gui.tacz.gun_refit.property_diagrams.ammo_capacity"), nameTextStartX, yOffset[0], fontColor, false);
                graphics.fill(barStartX, yOffset[0] + 2, barEndX, yOffset[0] + 6, barBackgroundColor);
                graphics.fill(barStartX, yOffset[0] + 2, barStartX + barMaxWidth, yOffset[0] + 6, barBaseColor);
                drawValue(graphics, font, "INV", x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);
            } else {
                int barrelBulletAmount = (iGun.hasBulletInBarrel(gunItem) && index.getGunData().getBolt() != Bolt.OPEN_BOLT) ? 1 : 0;
                int ammoAmount = gunData.getAmmoAmount() + barrelBulletAmount;
                double ammoAmountPercent = Math.min(ammoAmount / 100.0, 1);
                int ammoLength = (int) (barStartX + barMaxWidth * ammoAmountPercent);
                int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, index.getGunData()) + barrelBulletAmount;
                int addAmmoCount = Math.max(maxAmmoCount - ammoAmount, 0);
                int addAmmoCountLength = (int) (barMaxWidth * addAmmoCount / 100.0);

                graphics.text(font, Component.translatable("gui.tacz.gun_refit.property_diagrams.ammo_capacity"), nameTextStartX, yOffset[0], fontColor, false);
                graphics.fill(barStartX, yOffset[0] + 2, barEndX, yOffset[0] + 6, barBackgroundColor);
                graphics.fill(barStartX, yOffset[0] + 2, ammoLength, yOffset[0] + 6, barBaseColor);
                if (addAmmoCount > 0) {
                    int barRight = Math.min(ammoLength + addAmmoCountLength, barEndX);
                    graphics.fill(ammoLength, yOffset[0] + 2, barRight, yOffset[0] + 6, barPositivelyColor);
                    drawValue(graphics, font, String.format("%d §a(+%d)", ammoAmount, addAmmoCount), x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);
                } else {
                    drawValue(graphics, font, String.format("%d", ammoAmount), x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);
                }
            }

            yOffset[0] += 10;


            // 跑射延迟
            float sprintTime = gunData.getSprintTime();
            double sprintTimePercent = Mth.clamp(sprintTime / 0.5, 0, 1);
            int sprintLength = (int) (barStartX + barMaxWidth * sprintTimePercent);
            String sprintValueText = String.format("%.2fs", sprintTime);

            graphics.text(font, Component.translatable("gui.tacz.gun_refit.property_diagrams.sprint_time"), nameTextStartX, yOffset[0], fontColor, false);
            graphics.fill(barStartX, yOffset[0] + 2, barEndX, yOffset[0] + 6, barBackgroundColor);
            graphics.fill(barStartX, yOffset[0] + 2, sprintLength, yOffset[0] + 6, barBaseColor);
            drawValue(graphics, font, sprintValueText, x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);

            yOffset[0] += 10;

            AttachmentPropertyManager.getModifiers().forEach((key, value) -> value.getPropertyDiagramsData(gunItem, gunData, cacheProperty).forEach(data -> {
                double defaultPercent = data.defaultPercent();
                double modifierPercent = data.modifierPercent();
                double modifier = data.modifier().doubleValue();
                String titleKey = data.titleKey();
                String positivelyString = data.positivelyString();
                String negativeString = data.negativeString();
                String defaultString = data.defaultString();
                boolean positivelyBetter = data.positivelyBetter();

                defaultPercent = Mth.clamp(defaultPercent, 0, 1);
                int defaultLength = (int) (barStartX + barMaxWidth * defaultPercent);
                int modifierLength = Mth.clamp(defaultLength + (int) (barMaxWidth * modifierPercent), barStartX, barEndX);

                graphics.text(font, Component.translatable(titleKey), nameTextStartX, yOffset[0], fontColor, false);
                graphics.fill(barStartX, yOffset[0] + 2, barEndX, yOffset[0] + 6, barBackgroundColor);
                graphics.fill(barStartX, yOffset[0] + 2, defaultLength, yOffset[0] + 6, barBaseColor);
                if (modifier > 0) {
                    int barColor = positivelyBetter ? barPositivelyColor : barNegativeColor;
                    graphics.fill(defaultLength, yOffset[0] + 2, modifierLength, yOffset[0] + 6, barColor);
                    drawValue(graphics, font, positivelyString, x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);
                } else if (modifier < 0) {
                    int barColor = positivelyBetter ? barNegativeColor : barPositivelyColor;
                    graphics.fill(modifierLength, yOffset[0] + 2, defaultLength, yOffset[0] + 6, barColor);
                    drawValue(graphics, font, negativeString, x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);
                } else {
                    drawValue(graphics, font, defaultString, x, valueTextStartX, valueTextRightX, yOffset[0], fontColor);
                }
                yOffset[0] += 10;
            }));
        });
    }

    private static void drawValue(GuiGraphicsExtractor graphics, Font font, String value, int panelX, int preferredX, int rightX, int y, int color) {
        int minX = panelX + PANEL_PADDING;
        int textWidth = font.width(value);
        if (textWidth <= 0) {
            return;
        }
        int columnWidth = Math.max(1, rightX - preferredX);
        if (textWidth <= columnWidth) {
            graphics.text(font, value, preferredX, y, color, false);
            return;
        }
        int panelWidth = Math.max(1, rightX - minX);
        if (textWidth <= panelWidth) {
            graphics.text(font, value, rightX - textWidth, y, color, false);
            return;
        }

        graphics.text(font, value, minX, y, color, false);
    }
}
