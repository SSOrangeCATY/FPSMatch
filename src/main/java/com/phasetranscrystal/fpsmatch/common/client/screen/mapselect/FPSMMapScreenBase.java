package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 地图选择相关 Screen 的公共基类。
 * <p>
 * 封装统一的：
 * <ul>
 *   <li>多层背景绘制（替代原先 3 处重复的 renderMultiLayerBackground）</li>
 *   <li>列表面板背景绘制</li>
 *   <li>滚动条绘制</li>
 *   <li>按钮工厂方法（统一宽度/高度/y 坐标规则）</li>
 * </ul>
 * 所有颜色取自 {@link FPSMGuiTheme}，消除散落硬编码。
 */
public abstract class FPSMMapScreenBase extends Screen {
    protected FPSMMapScreenBase(Component title) {
        super(title);
    }

    /**
     * 绘制全屏多层背景：阴影 + 外边框 + 主背景 + 内边框。
     * 替代原先在 Selection/Settings/Detail 三个类中复制的 8 层 fill。
     */
    protected void drawMultiLayerBackground(GuiGraphics graphics) {
        graphics.fill(2, 2, width + 2, height + 2, FPSMGuiTheme.BG_SHADOW);
        // 外边框
        graphics.fill(0, 0, width, 1, FPSMGuiTheme.BORDER_OUTER);
        graphics.fill(0, height - 1, width, height, FPSMGuiTheme.BORDER_OUTER);
        graphics.fill(0, 1, 1, height - 1, FPSMGuiTheme.BORDER_OUTER);
        graphics.fill(width - 1, 1, width, height - 1, FPSMGuiTheme.BORDER_OUTER);
        // 主背景
        graphics.fill(1, 1, width - 1, height - 1, FPSMGuiTheme.BG_BASE);
        // 内边框
        int p = FPSMGuiTheme.PADDING;
        graphics.fill(1 + p, 1 + p, width - 1 - p, 1 + p + 1, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(1 + p, height - 1 - p - 1, width - 1 - p, height - 1 - p, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(1 + p, 1 + p + 1, 1 + p + 1, height - 1 - p - 1, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(width - 1 - p - 1, 1 + p + 1, width - 1 - p, height - 1 - p - 1, FPSMGuiTheme.BORDER_INNER);
    }

    /**
     * 绘制列表面板背景：半透明深色底 + 上下边框线。
     * 替代原先散落的 graphics.fill(..., 0x77000000) + 0xFF666666 边框。
     */
    protected void drawListBackground(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, FPSMGuiTheme.BG_LIST);
        graphics.fill(left, top, right, top + 1, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(left, bottom - 1, right, bottom, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(left, top, left + 1, bottom, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(right - 1, top, right, bottom, FPSMGuiTheme.BORDER_INNER);
    }

    /**
     * 绘制矩形面板背景（用于详情页内容区、卡片等）。
     */
    protected void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int bgColor) {
        graphics.fill(left, top, right, bottom, bgColor);
        graphics.fill(left, top, right, top + 1, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(left, bottom - 1, right, bottom, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(left, top, left + 1, bottom, FPSMGuiTheme.BORDER_INNER);
        graphics.fill(right - 1, top, right, bottom, FPSMGuiTheme.BORDER_INNER);
    }

    protected void drawScreenTitle(GuiGraphics graphics, Component title, Component subtitle, int titleY) {
        graphics.drawCenteredString(font, title, width / 2, titleY, FPSMGuiTheme.TEXT_TITLE);
        if (subtitle != null) {
            graphics.drawCenteredString(font, subtitle, width / 2, titleY + 18, FPSMGuiTheme.TEXT_SUB);
        }
    }

    protected void drawEmptyState(GuiGraphics graphics, Component message, int centerX, int centerY) {
        int textWidth = font.width(message);
        int panelWidth = Math.max(160, textWidth + 36);
        int panelHeight = 34;
        int left = centerX - panelWidth / 2;
        int top = centerY - panelHeight / 2;
        drawPanel(graphics, left, top, left + panelWidth, top + panelHeight, FPSMGuiTheme.BG_PANEL);
        graphics.drawCenteredString(font, message, centerX, top + 13, FPSMGuiTheme.TEXT_MUTED);
    }

    protected void drawRowBackground(GuiGraphics graphics, int left, int top, int right, int bottom, boolean selected, boolean hovered, boolean disabled) {
        int color = disabled ? FPSMGuiTheme.ROW_DISABLED : selected ? FPSMGuiTheme.ROW_SELECTED : hovered ? FPSMGuiTheme.ROW_HOVER : FPSMGuiTheme.ROW_NORMAL;
        graphics.fill(left, top, right, bottom, color);
    }

    protected void drawStatusChip(GuiGraphics graphics, Component text, int x, int y, int color) {
        int chipWidth = font.width(text) + 12;
        graphics.fill(x, y, x + chipWidth, y + 14, FPSMGuiTheme.CHIP_BG);
        graphics.fill(x, y, x + chipWidth, y + 1, FPSMGuiTheme.CHIP_BORDER);
        graphics.fill(x, y + 13, x + chipWidth, y + 14, FPSMGuiTheme.CHIP_BORDER);
        graphics.fill(x, y, x + 1, y + 14, color);
        graphics.drawString(font, text, x + 7, y + 3, color, false);
    }

    protected void drawClippedString(GuiGraphics graphics, Component text, int x, int y, int color, int maxWidth) {
        graphics.drawString(font, clipped(text, maxWidth), x, y, color, false);
    }

    protected Component clipped(Component text, int maxWidth) {
        String value = text.getString();
        if (font.width(value) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int allowedWidth = Math.max(0, maxWidth - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(value, allowedWidth) + ellipsis);
    }

    protected void drawSectionLabel(GuiGraphics graphics, Component text, int x, int y) {
        graphics.drawString(font, text, x, y, FPSMGuiTheme.TEXT_TITLE, false);
        graphics.fill(x, y + 11, x + Math.max(24, font.width(text)), y + 12, FPSMGuiTheme.ACCENT_PRIMARY);
    }

    /**
     * 绘制滚动条。统一所有列表的滚动条样式。
     *
     * @param barX         滚动条 x 坐标
     * @param barY         滚动条 y 坐标
     * @param barHeight    滚动条总高度
     * @param scroll       当前滚动偏移
     * @param maxScroll    最大滚动偏移
     * @param totalItems   总条目数
     * @param visibleItems 可见条目数
     */
    protected void drawScrollBar(GuiGraphics graphics, int barX, int barY, int barHeight,
                                 int scroll, int maxScroll, int totalItems, int visibleItems) {
        if (maxScroll <= 0) return;
        int barWidth = FPSMGuiTheme.SCROLL_BAR_WIDTH;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, FPSMGuiTheme.SCROLL_TRACK);
        int thumbSize = Math.max(10, barHeight * visibleItems / Math.max(1, totalItems));
        int thumbY = barY + scroll * (barHeight - thumbSize) / Math.max(1, maxScroll);
        graphics.fill(barX, thumbY, barX + barWidth, thumbY + thumbSize, FPSMGuiTheme.SCROLL_THUMB);
    }

    // ===== 按钮工厂方法 =====

    /**
     * 创建小按钮（行内操作：apply/kick/invite/edit）。
     */
    protected Button createSmallButton(Component text, int x, int y, Button.OnPress onPress) {
        return Button.builder(text, onPress)
                .bounds(x, y, FPSMGuiTheme.BUTTON_SMALL_WIDTH, FPSMGuiTheme.BUTTON_HEIGHT)
                .build();
    }

    /**
     * 创建中按钮（主操作：join/leave/detail/refresh/debug）。
     */
    protected Button createMediumButton(Component text, int x, int y, Button.OnPress onPress) {
        return Button.builder(text, onPress)
                .bounds(x, y, FPSMGuiTheme.BUTTON_MEDIUM_WIDTH, FPSMGuiTheme.BUTTON_HEIGHT)
                .build();
    }

    /**
     * 创建大按钮（返回/确认：back/done/accept）。
     */
    protected Button createLargeButton(Component text, int x, int y, Button.OnPress onPress) {
        return Button.builder(text, onPress)
                .bounds(x, y, FPSMGuiTheme.BUTTON_LARGE_WIDTH, FPSMGuiTheme.BUTTON_HEIGHT)
                .build();
    }

    /**
     * 创建返回按钮，统一位于底部居中。
     */
    protected Button createBackButton(Button.OnPress onPress) {
        return createLargeButton(Component.translatable("gui.back"),
                width / 2 - FPSMGuiTheme.BUTTON_LARGE_WIDTH / 2,
                height - 52, onPress);
    }

    /**
     * 计算等间距排列的按钮 x 坐标。
     *
     * @param centerX     中心 x
     * @param buttonWidth 单个按钮宽度
     * @param gap         按钮间距
     * @param index       按钮索引（从 0 开始）
     * @param total       按钮总数
     * @return 该索引按钮的 x 坐标
     */
    protected static int buttonX(int centerX, int buttonWidth, int gap, int index, int total) {
        int totalWidth = total * buttonWidth + (total - 1) * gap;
        int startX = centerX - totalWidth / 2;
        return startX + index * (buttonWidth + gap);
    }
}
