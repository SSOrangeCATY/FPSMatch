package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于LDLib WidgetGroup的AbstractContainerScreen基类。
 * 保留容器/槽位系统，同时使用LDLib Widget进行UI绘制。
 */
public abstract class FPSMWidgetContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected final WidgetGroup root = new WidgetGroup(0, 0, 0, 0);
    private final List<Component> pendingTooltips = new ArrayList<>();

    public FPSMWidgetContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        root.setClientSideWidget();
    }

    @Override
    protected void init() {
        super.init();
        root.setSize(new Size(width, height));
        root.setSelfPosition(Position.ORIGIN);
        root.initWidget();
        buildUI();
    }

    protected void rebuildUI() {
        root.widgets.clear();
        root.setSize(new Size(width, height));
        buildUI();
    }

    protected abstract void buildUI();

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
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
}