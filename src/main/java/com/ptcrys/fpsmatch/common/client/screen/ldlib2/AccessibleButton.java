package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import java.util.Objects;
import java.util.function.Supplier;

/** LDLib2 button with a keyboard-equivalent activation path and narration metadata. */
public class AccessibleButton extends Button implements Ldlib2AccessibilityController.FocusTarget {

    private final KeyboardActivationLatch activationLatch = new KeyboardActivationLatch();
    private UIEventListener action;
    private Supplier<Component> accessibleName = () -> text.getText();
    private Supplier<Component> accessibleState = Component::empty;
    private Supplier<Component> accessibleHint = Component::empty;

    public AccessibleButton() {
        setFocusable(true);
        Ldlib2AccessibilityController.installKeyboardActivation(
                this,
                activationLatch,
                this::canActivate,
                this::activate);
    }

    @Override
    public AccessibleButton setOnClick(UIEventListener action) {
        this.action = action;
        super.setOnClick(action);
        return this;
    }

    @Override
    public AccessibleButton setText(Component text) {
        super.setText(text);
        return this;
    }

    @Override
    public AccessibleButton setText(String text) {
        super.setText(text);
        return this;
    }

    @Override
    public AccessibleButton setText(String text, boolean translate) {
        super.setText(text, translate);
        return this;
    }

    public AccessibleButton setAccessibleName(Component name) {
        return setAccessibleName(() -> name);
    }

    public AccessibleButton setAccessibleName(Supplier<Component> name) {
        accessibleName = Objects.requireNonNull(name, "name");
        return this;
    }

    public AccessibleButton setAccessibleState(Supplier<Component> state) {
        accessibleState = Objects.requireNonNull(state, "state");
        return this;
    }

    public AccessibleButton setAccessibleHint(Supplier<Component> hint) {
        accessibleHint = Objects.requireNonNull(hint, "hint");
        return this;
    }

    @Override
    public void activate() {
        if (action != null && isActive()) {
            action.handleEvent(Ldlib2AccessibilityController.keyboardActivationEvent(this));
        }
    }

    @Override
    public boolean canActivate() {
        return action != null && isActive();
    }

    @Override
    public AccessibleButton element() {
        return this;
    }

    @Override
    public Supplier<Component> accessibleName() {
        return accessibleName;
    }

    @Override
    public Supplier<Component> state() {
        return accessibleState;
    }

    @Override
    public Supplier<Component> hint() {
        return accessibleHint;
    }

    @Override
    public void resetActivationLatch() {
        activationLatch.reset();
    }

    @Override
    public void drawBackgroundOverlay(GUIContext context) {
        super.drawBackgroundOverlay(context);
        FPSMLdlib2Theme.drawFocusRing(this, context);
    }
}
