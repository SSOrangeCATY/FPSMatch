package com.tacz.guns.client.tooltip;

import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class ClientBlockItemTooltip implements ClientTooltipComponent {
    private final Identifier blockId;
    private final List<Component> components = Lists.newArrayList();
    private @Nullable MutableComponent packInfo;

    public ClientBlockItemTooltip(BlockItemTooltip tooltip) {
        this.blockId = tooltip.getBlockId();
        this.addText();
        this.addPackInfo();
    }

    private void addPackInfo() {
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
        if (packInfoObject != null) {
            packInfo = Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC);
        }
    }


    @Override
    public int getHeight(Font font) {
        return components.size() * 10 + (packInfo != null ? 16 : 0);
    }

    @Override
    public int getWidth(Font font) {
        int[] width = new int[]{0};
        if (packInfo != null) {
            width[0] = Math.max(width[0], font.width(packInfo) + 4);
        }
        components.forEach(c -> width[0] = Math.max(width[0], font.width(c)));
        return width[0];
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
        drawText(graphics, font, pX, pY);
    }

    private void drawText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
        int yOffset = pY;
        for (Component component : this.components) {
            graphics.text(font, component, pX, yOffset, 0xffaa00, false);
            yOffset += 10;
        }
        // 枪包名
        if (packInfo != null) {
            graphics.text(font, this.packInfo, pX, yOffset + 6, 0xffffff, false);
        }
    }

    @Override
    public void extractImage(Font font, int mouseX, int mouseY, int width, int height, GuiGraphicsExtractor gui) {
    }

    private void addText() {
        TimelessAPI.getClientBlockIndex(blockId).ifPresent(index -> {
            @Nullable String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                String text = I18n.get(tooltipKey);
                String[] split = text.split("\n");
                Arrays.stream(split).forEach(s -> components.add(Component.literal(s).withStyle(ChatFormatting.GRAY)));
            }
        });
    }
}
