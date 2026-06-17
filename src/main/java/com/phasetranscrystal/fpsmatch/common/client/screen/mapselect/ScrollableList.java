package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 可滚动列表抽象，统一管理滚动偏移、可见行计算、裁剪渲染、滚动条绘制。
 * <p>
 * 修复原先 Shop/Manage/Invite 三个界面"仅渲染 visibleRows 行、超出直接丢失且不可滚动"的 bug。
 * <p>
 * 用法：子类实现 {@link #renderRow} 渲染单行，{@link #totalItems} 返回总数。
 * 滚轮事件由 {@link #handleMouseScrolled} 处理。
 */
public abstract class ScrollableList {
    private final int left;
    private final int top;
    private final int width;
    private final int rowHeight;
    private final int rowGap;
    private final int bottom;
    private int scrollOffset;

    /**
     * @param left      列表左 x
     * @param top       列表顶 y
     * @param width     列表宽
     * @param bottom    列表底 y（不含）
     * @param rowHeight 单行高
     * @param rowGap    行间距
     */
    protected ScrollableList(int left, int top, int width, int bottom, int rowHeight, int rowGap) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.bottom = bottom;
        this.rowHeight = rowHeight;
        this.rowGap = rowGap;
    }

    /** 子类返回列表总条目数 */
    public abstract int totalItems();

    /** 子类渲染第 index 行（已处于 scissor 裁剪上下文中） */
    protected abstract void renderRow(GuiGraphics graphics, int index, int rowTop, int mouseX, int mouseY);

    /** 单行高 + 间距 */
    public final int rowStride() {
        return rowHeight + rowGap;
    }

    /** 可见行数 */
    public final int visibleRows() {
        return Math.max(1, (bottom - top + rowGap) / rowStride());
    }

    /** 最大滚动偏移 */
    public final int maxScrollOffset() {
        return Math.max(0, totalItems() - visibleRows());
    }

    public final int scrollOffset() {
        return scrollOffset;
    }

    public final void setScrollOffset(int offset) {
        this.scrollOffset = Mth.clamp(offset, 0, maxScrollOffset());
    }

    public final int left() {
        return left;
    }

    public final int top() {
        return top;
    }

    public final int listWidth() {
        return width;
    }

    public final int bottom() {
        return bottom;
    }

    public final int rowHeight() {
        return rowHeight;
    }

    /** 列表高度 */
    public final int listHeight() {
        return bottom - top;
    }

    /**
     * 处理滚轮事件。返回 true 表示已消费。
     */
    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < left || mouseX > left + width || mouseY < top || mouseY > bottom) {
            return false;
        }
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScrollOffset());
        return true;
    }

    /**
     * 根据鼠标坐标返回点击的行索引，-1 表示未点中。
     */
    public int indexAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX > left + width || mouseY < top || mouseY > bottom) {
            return -1;
        }
        int relativeY = (int) mouseY - top;
        int row = relativeY / rowStride();
        if (relativeY % rowStride() > rowHeight) {
            return -1;
        }
        int index = scrollOffset + row;
        return index < totalItems() ? index : -1;
    }

    /**
     * 渲染整个列表：裁剪 + 遍历可见行 + 滚动条。
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset());
        int total = totalItems();
        int visible = visibleRows();
        int end = Math.min(total, scrollOffset + visible);

        graphics.enableScissor(left, top, left + width, bottom);
        for (int i = scrollOffset; i < end; i++) {
            int rowTop = top + (i - scrollOffset) * rowStride();
            renderRow(graphics, i, rowTop, mouseX, mouseY);
        }
        graphics.disableScissor();

        // 滚动条绘制在列表右侧
        int barX = left + width - FPSMGuiTheme.SCROLL_BAR_WIDTH - 2;
        drawScrollBar(graphics, barX, top, listHeight(), scrollOffset, maxScrollOffset(), total, visible);
    }

    /** 滚动条绘制（委托给主题色） */
    private void drawScrollBar(GuiGraphics graphics, int barX, int barY, int barHeight,
                               int scroll, int maxScroll, int totalItems, int visibleItems) {
        if (maxScroll <= 0) return;
        int barWidth = FPSMGuiTheme.SCROLL_BAR_WIDTH;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, FPSMGuiTheme.SCROLL_TRACK);
        int thumbSize = Math.max(10, barHeight * visibleItems / Math.max(1, totalItems));
        int thumbY = barY + scroll * (barHeight - thumbSize) / Math.max(1, maxScroll);
        graphics.fill(barX, thumbY, barX + barWidth, thumbY + thumbSize, FPSMGuiTheme.SCROLL_THUMB);
    }
}
