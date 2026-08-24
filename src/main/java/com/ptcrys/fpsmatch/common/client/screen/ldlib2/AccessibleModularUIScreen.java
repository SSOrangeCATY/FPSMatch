package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;

/** Base screen that adds deterministic traversal and narration to LDLib2. */
public abstract class AccessibleModularUIScreen extends ModularUIScreen {
    private final Ldlib2AccessibilityController accessibility;
    private UIElement directPointerTarget;
    private int directPointerButton = -1;
    private float directPointerStartX;
    private float directPointerStartY;
    private boolean modularUiRemoved;

    protected AccessibleModularUIScreen(ModularUI modularUI, Component title) {
        super(modularUI, title);
        accessibility = new Ldlib2AccessibilityController(modularUI, title);
    }

    protected final Ldlib2AccessibilityController accessibility() {
        return accessibility;
    }

    protected final void registerFocusGroup(
            Supplier<List<Ldlib2AccessibilityController.FocusTarget>> targets
    ) {
        accessibility.registerGroup(targets);
    }

    @Override
    public void init() {
        super.init();
        accessibility.reconcileFocus();
    }

    @Override
    public void tick() {
        super.tick();
        accessibility.reconcileFocus();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (directPointerTarget != null) {
            return false;
        }
        UIElement target = hitElementAt(mouseX, mouseY);
        if (target == null) {
            return false;
        }
        if (target == modularUI.getLastHoveredElement()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        UIEvent mouseDown = pointerEvent(UIEvents.MOUSE_DOWN, target, mouseX, mouseY, button);
        UIEventDispatcher.dispatchEvent(mouseDown);
        if (!mouseDown.hasHandler) {
            return false;
        }

        focusPointerTarget(target);
        directPointerTarget = target;
        directPointerButton = button;
        directPointerStartX = (float) (mouseX - modularUI.getLeftPos());
        directPointerStartY = (float) (mouseY - modularUI.getTopPos());
        setFocused(modularUI.getWidget());
        if (button == 0) {
            setDragging(true);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (directPointerTarget == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button != directPointerButton) {
            return false;
        }

        UIEvent drag = pointerEvent(
                UIEvents.DRAG_UPDATE, directPointerTarget, mouseX, mouseY, button
        );
        drag.target = directPointerTarget;
        drag.deltaX = (float) dragX;
        drag.deltaY = (float) dragY;
        drag.dragStartX = directPointerStartX;
        drag.dragStartY = directPointerStartY;
        UIEventDispatcher.dispatchEvent(drag, true, true, false);
        return drag.hasHandler;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (directPointerTarget == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        if (button != directPointerButton) {
            return false;
        }

        UIElement pressedTarget = directPointerTarget;
        int pressedButton = directPointerButton;
        directPointerTarget = null;
        directPointerButton = -1;
        directPointerStartX = 0;
        directPointerStartY = 0;
        setDragging(false);

        UIElement releaseTarget = hitElementAt(mouseX, mouseY);
        UIEvent mouseUp = pointerEvent(
                UIEvents.MOUSE_UP, pressedTarget, mouseX, mouseY, pressedButton
        );
        mouseUp.target = pressedTarget;
        RuntimeException releaseFailure = null;
        try {
            UIEventDispatcher.dispatchEvent(mouseUp);
        } catch (RuntimeException failure) {
            releaseFailure = failure;
        } finally {
            try {
                modularUI.getDragHandler().stopDrag(releaseTarget);
            } catch (RuntimeException failure) {
                releaseFailure = mergeFailure(releaseFailure, failure);
            }
        }
        if (releaseFailure != null) {
            throw releaseFailure;
        }

        if (releaseTarget == pressedTarget) {
            UIEvent click = pointerEvent(
                    UIEvents.CLICK, pressedTarget, mouseX, mouseY, pressedButton
            );
            UIEventDispatcher.dispatchEvent(click);
        }
        return true;
    }

    private void focusPointerTarget(UIElement target) {
        if (target.isFocusable()) {
            if (target.isActive()) {
                modularUI.requestFocus(target);
            }
            return;
        }

        modularUI.clearFocus();
        List<UIElement> path = target.getStructurePath();
        for (int index = path.size() - 1; index >= 0; index--) {
            UIElement candidate = path.get(index);
            if (candidate.isFocusable()) {
                modularUI.requestFocus(candidate);
                return;
            }
        }
    }

    protected final UIElement hitElementAt(double mouseX, double mouseY) {
        var hit = modularUI.ui.rootElement.hitTest(
                mouseX - modularUI.getLeftPos(), mouseY - modularUI.getTopPos()
        );
        return hit == null ? null : hit.getA();
    }

    private UIEvent pointerEvent(
            String type,
            UIElement target,
            double mouseX,
            double mouseY,
            int button
    ) {
        UIEvent event = UIEvent.create(type);
        event.x = (float) (mouseX - modularUI.getLeftPos());
        event.y = (float) (mouseY - modularUI.getTopPos());
        event.button = button;
        event.target = target;
        return event;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW_KEY_TAB
                && accessibility.moveFocus((modifiers & GLFW_MOD_SHIFT) != 0)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return accessibility.keyReleased(keyCode, scanCode, modifiers)
                || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        accessibility.updateNarrationState(output);
    }

    protected final void announce(Component message, boolean error) {
        accessibility.announce(message, error);
        triggerImmediateNarration(true);
    }

    @Override
    public void removed() {
        boolean stopDrag = modularUI.getDragHandler().isDragging();
        directPointerTarget = null;
        directPointerButton = -1;
        directPointerStartX = 0;
        directPointerStartY = 0;
        setDragging(false);

        RuntimeException teardownFailure = null;
        if (!modularUiRemoved) {
            modularUiRemoved = true;
            try {
                modularUI.onRemoved();
            } catch (RuntimeException failure) {
                teardownFailure = mergeFailure(teardownFailure, failure);
            }
        }
        if (stopDrag) {
            try {
                modularUI.getDragHandler().stopDrag(null);
            } catch (RuntimeException failure) {
                teardownFailure = mergeFailure(teardownFailure, failure);
            }
        }
        try {
            accessibility.clearFocus();
        } catch (RuntimeException failure) {
            teardownFailure = mergeFailure(teardownFailure, failure);
        }
        try {
            super.removed();
        } catch (RuntimeException failure) {
            teardownFailure = mergeFailure(teardownFailure, failure);
        }
        if (teardownFailure != null) {
            throw teardownFailure;
        }
    }

    private static RuntimeException mergeFailure(
            RuntimeException current,
            RuntimeException next
    ) {
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }
}
