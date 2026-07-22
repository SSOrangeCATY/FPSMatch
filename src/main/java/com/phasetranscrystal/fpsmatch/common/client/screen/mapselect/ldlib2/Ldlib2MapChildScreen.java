package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapDetailChildScreen;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Shared lifecycle for LDLib2 map-room child pages. */
public abstract class Ldlib2MapChildScreen extends ModularUIScreen implements FPSMMapDetailChildScreen {
    protected final Screen parent;
    protected MapRoomDetail detail;
    private UIElement fallbackClickTarget;

    protected Ldlib2MapChildScreen(ModularUI ui, Component title, MapRoomDetail detail, Screen parent) {
        super(ui, title);
        this.detail = Objects.requireNonNull(detail, "detail");
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = Objects.requireNonNull(detail, "detail");
        onDetailApplied();
    }

    protected void onDetailApplied() {
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        modularUI.getWidget().mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIElement target = hitElementAt(mouseX, mouseY);
        if (target == modularUI.getLastHoveredElement()
                && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (target == null) {
            return false;
        }
        UIEvent event = mouseEvent("mouseDown", target, mouseX, mouseY, button);
        UIEventDispatcher.dispatchEvent(event);
        fallbackClickTarget = event.hasHandler ? target : null;
        return fallbackClickTarget != null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        UIElement pressedTarget = fallbackClickTarget;
        fallbackClickTarget = null;
        if (pressedTarget != null && !handled) {
            UIElement releasedTarget = hitElementAt(mouseX, mouseY);
            if (releasedTarget != null) {
                UIEvent mouseUp = mouseEvent("mouseUp", releasedTarget, mouseX, mouseY, button);
                UIEventDispatcher.dispatchEvent(mouseUp);
                if (releasedTarget == pressedTarget) {
                    UIEventDispatcher.dispatchEvent(mouseEvent("mouseClick", releasedTarget, mouseX, mouseY, button));
                }
            }
            return true;
        }
        return handled || pressedTarget != null;
    }

    /**
     * LDLib2 normally routes wheel input through the element hovered by the real cursor. The
     * process-local agent (and a few screen stacks) can provide coordinates without updating that
     * hover cache, so dispatch the same event against the hit-tested element as a fallback.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, scrollY);
        UIElement target = hitElementAt(mouseX, mouseY);
        if (handled || target == null || target == modularUI.getLastHoveredElement()) {
            return handled;
        }
        UIEvent event = UIEvent.create("mouseWheel");
        event.x = (float) (mouseX - modularUI.getLeftPos());
        event.y = (float) (mouseY - modularUI.getTopPos());
        event.deltaX = 0;
        event.deltaY = (float) scrollY;
        event.target = target;
        UIEventDispatcher.dispatchEvent(event);
        return event.hasHandler;
    }

    private UIEvent mouseEvent(String type, UIElement target, double mouseX, double mouseY, int button) {
        UIEvent event = UIEvent.create(type);
        event.x = (float) (mouseX - modularUI.getLeftPos());
        event.y = (float) (mouseY - modularUI.getTopPos());
        event.button = button;
        event.target = target;
        return event;
    }

    protected final UIElement hitElementAt(double mouseX, double mouseY) {
        var hit = modularUI.ui.rootElement.hitTest(
                mouseX - modularUI.getLeftPos(), mouseY - modularUI.getTopPos());
        return hit == null ? null : hit.getA();
    }

    @Override
    public void removed() {
        modularUI.onRemoved();
        super.removed();
    }
}
