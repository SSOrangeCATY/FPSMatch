package com.tacz.guns.client.tooltip;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Consumer;

public final class AttachmentTooltipTextBuilder {
    private AttachmentTooltipTextBuilder() {
    }

    public static void appendAttachmentText(ItemStack attachment,
                                            Identifier attachmentId,
                                            AttachmentType type,
                                            Consumer<Component> lines) {
        TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresent(index -> {
            AttachmentData data = index.getData();

            @Nullable String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                Arrays.stream(I18n.get(tooltipKey).split("\n"))
                        .map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY))
                        .forEach(lines);
            }

            if (attachment.getItem() instanceof IAttachment iAttachment) {
                if (iAttachment.hasCustomLaserColor(attachment)) {
                    int color = iAttachment.getLaserColor(attachment);
                    lines.accept(Component.translatable("tooltip.tacz.attachment.laser.color", rgbToHex(color))
                            .withStyle(Style.EMPTY.withColor(color)));
                } else if (index.getLaserConfig() != null) {
                    int color = index.getLaserConfig().getDefaultColor();
                    lines.accept(Component.translatable("tooltip.tacz.attachment.laser.color", rgbToHex(color))
                            .withStyle(Style.EMPTY.withColor(color)));
                }
            }

            if (type == AttachmentType.SCOPE) {
                float[] zoom = index.getZoom();
                if (zoom != null) {
                    String[] zoomText = new String[zoom.length];
                    for (int i = 0; i < zoom.length; i++) {
                        zoomText[i] = "x" + zoom[i];
                    }
                    lines.accept(Component.translatable("tooltip.tacz.attachment.zoom", StringUtils.join(zoomText, ", "))
                            .withStyle(ChatFormatting.GOLD));
                }
            }

            if (type == AttachmentType.EXTENDED_MAG) {
                int magLevel = data.getExtendedMagLevel();
                if (magLevel == 1) {
                    lines.accept(Component.translatable("tooltip.tacz.attachment.extended_mag_level_1").withStyle(ChatFormatting.GRAY));
                } else if (magLevel == 2) {
                    lines.accept(Component.translatable("tooltip.tacz.attachment.extended_mag_level_2").withStyle(ChatFormatting.BLUE));
                } else if (magLevel == 3) {
                    lines.accept(Component.translatable("tooltip.tacz.attachment.extended_mag_level_3").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }

            data.getModifier().forEach((key, value) -> value.getComponents().forEach(lines));
        });

        if (Minecraft.getInstance().hasShiftDown()) {
            lines.accept(Component.translatable("tooltip.tacz.attachment.yaw.support").withStyle(ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("tooltip.tacz.attachment.yaw.shift").withStyle(ChatFormatting.GRAY));
        }

        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(attachmentId);
        if (packInfoObject != null) {
            lines.accept(Component.translatable(packInfoObject.getName())
                    .withStyle(ChatFormatting.BLUE)
                    .withStyle(ChatFormatting.ITALIC));
        }
    }

    private static String rgbToHex(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
