package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于LDLib WidgetGroup的Screen基类，替代原版Screen的手动绘制方式。
 * 子类在构造后通过 {@link #root} WidgetGroup添加组件即可。
 */
public class FPSMWidgetScreen extends Screen {
    protected final WidgetGroup root;
    private final List<Component> pendingTooltips = new ArrayList<>();

    public FPSMWidgetScreen(Component title) {
        super(title);
        this.root = new WidgetGroup(0, 0, 0, 0);
        this.root.setClientSideWidget();
    }

    @Override
    protected void init() {
        root.setSize(new Size(width, height));
        root.setSelfPosition(Position.ORIGIN);
        root.initWidget();
        buildUI();
    }

    /**
     * 子类在此方法中向root添加Widget组件。
     * 在init()中自动调用，此时width/height已确定。
     */
    protected void buildUI() {
    }

    /**
     * 重建UI：清空root并重新调用buildUI。
     */
    protected void rebuildUI() {
        root.widgets.clear();
        root.setSize(new Size(width, height));
        buildUI();
    }

    @Override
    public void tick() {
        root.updateScreen();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        root.drawInBackground(graphics, mouseX, mouseY, partialTick);
        root.drawInForeground(graphics, mouseX, mouseY, partialTick);
        root.drawOverlay(graphics, mouseX, mouseY, partialTick);
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        pendingTooltips.clear();
        collectTooltips(root, mouseX, mouseY);
        if (!pendingTooltips.isEmpty()) {
            graphics.renderComponentTooltip(font, pendingTooltips, mouseX, mouseY);
        }
    }

    private void collectTooltips(Widget widget, double mouseX, double mouseY) {
        if (!widget.isVisible()) return;
        if (widget instanceof WidgetGroup group) {
            for (int i = group.widgets.size() - 1; i >= 0; i--) {
                collectTooltips(group.widgets.get(i), mouseX, mouseY);
            }
        }
        if (!widget.getTooltipTexts().isEmpty() && widget.isMouseOverElement(mouseX, mouseY)) {
            Widget hovered = widget.getHoverElement(mouseX, mouseY);
            if (hovered == widget) {
                pendingTooltips.addAll(widget.getTooltipTexts());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Widget hovered = root.getHoverElement(mouseX, mouseY);
        if (hovered != null && hovered.isActive() && hovered.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Widget hovered = root.getHoverElement(mouseX, mouseY);
        if (hovered != null && hovered.isActive() && hovered.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        Widget hovered = root.getHoverElement(mouseX, mouseY);
        if (hovered != null && hovered.isActive() && hovered.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Widget hovered = root.getHoverElement(mouseX, mouseY);
        if (hovered != null && hovered.isActive() && hovered.mouseWheelMove(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Widget widget : root.getContainedWidgets(true)) {
            if (widget.isActive() && widget.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (Widget widget : root.getContainedWidgets(true)) {
            if (widget.isActive() && widget.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === 公共工具方法 ===

    /** 创建半透明背景 */
    public static ColorRectTexture panelBackground(int color) {
        return new ColorRectTexture(color);
    }

    /** 默认面板背景色 0x77000000 */
    public static ColorRectTexture defaultPanelBg() {
        return new ColorRectTexture(0x77000000);
    }

    /** 默认多层背景色常量 */
    public static final int COLOR_SHADOW = 0x80000000;
    public static final int COLOR_MAIN_BG = 0xFF444444;
    public static final int COLOR_INNER_BORDER = 0xFF666666;
    public static final int COLOR_OUTER_BORDER = 0xFF222222;
}