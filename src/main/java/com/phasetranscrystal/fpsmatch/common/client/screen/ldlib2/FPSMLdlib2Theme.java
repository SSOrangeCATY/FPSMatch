package com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import net.minecraft.network.chat.Component;

/** Shared visual language for FPSMatch LDLib2 work surfaces. */
public final class FPSMLdlib2Theme {
    public static final int BG = 0xF20B1016;
    public static final int SURFACE = 0xF2151B23;
    public static final int ELEVATED = 0xF21C242E;
    public static final int BORDER = 0xFF34404D;
    public static final int BORDER_SOFT = 0xFF28323D;
    public static final int ACCENT = 0xFF4BB3FD;
    public static final int ACCENT_DARK = 0xFF246C9D;
    public static final int SUCCESS = 0xFF5CC68A;
    public static final int WARNING = 0xFFF0C45B;
    public static final int DANGER = 0xFFEA6262;
    public static final int TEXT = 0xFFF2F5F7;
    public static final int MUTED = 0xFF9AA7B3;
    public static final int DISABLED = 0xFF65717D;
    public static final int SETTINGS_CATEGORY = 0xFF2A3641;
    public static final int SETTINGS_ENTRY = 0xFF111820;
    public static final int SETTINGS_CATEGORY_TEXT = 0xFFE4ECF2;
    public static final int SETTINGS_ENTRY_TEXT = 0xFFBAC5CE;
    public static final int HOLD_ACTION_PROGRESS = 0xFFEA6262;

    private FPSMLdlib2Theme() {
    }

    public enum ButtonKind {
        PRIMARY, SECONDARY, DANGER, QUIET
    }

    public static void root(UIElement element) {
        element.style(style -> style.background(panelTexture(BG, BORDER_SOFT)));
    }

    public static void panel(UIElement element) {
        element.style(style -> style.background(panelTexture(SURFACE, BORDER)));
    }

    public static void elevated(UIElement element) {
        element.style(style -> style.background(panelTexture(ELEVATED, BORDER)));
    }

    public static void settingsCategory(UIElement element) {
        element.style(style -> style.background(panelTexture(SETTINGS_CATEGORY, 0xFF465361)));
    }

    public static void settingsEntry(UIElement element) {
        element.style(style -> style.background(panelTexture(SETTINGS_ENTRY, 0xFF242E38)));
    }

    public static void settingsCategoryToggle(Toggle toggle) {
        toggle.style(style -> style.background(panelTexture(SETTINGS_ENTRY, 0xFF242E38)));
        toggle.toggleStyle(style -> style
                .baseTexture(panelTexture(SETTINGS_ENTRY, 0xFF242E38))
                .hoverTexture(panelTexture(0xFF202B35, BORDER))
                .unmarkTexture(panelTexture(0xFF0D1319, BORDER))
                .markTexture(IGuiTexture.group(
                        panelTexture(ACCENT_DARK, ACCENT), Icons.CHECK_SPRITE)));
        toggle.toggleLabel(label -> label.textStyle(style -> style
                .fontSize(7).textColor(SETTINGS_ENTRY_TEXT).textShadow(false)));
    }

    public static void settingsScroller(ScrollerView scroller) {
        scroller.style(style -> style.background(new ColorRectTexture(0xFF0F151C)));
        scroller.viewPort(element -> element.style(style ->
                style.background(new ColorRectTexture(0xFF0F151C))));
        scroller.viewContainer(element -> element.style(style ->
                style.background(new ColorRectTexture(0xFF0F151C))));
    }

    public static void title(Label label) {
        label.textStyle(style -> style.fontSize(15).textColor(TEXT).textShadow(true));
    }

    public static void sectionTitle(Label label) {
        label.textStyle(style -> style.fontSize(12).textColor(TEXT).textShadow(false));
    }

    public static void body(Label label) {
        label.textStyle(style -> style.fontSize(10).textColor(TEXT).lineSpacing(2));
    }

    public static void muted(Label label) {
        label.textStyle(style -> style.fontSize(9).textColor(MUTED).textShadow(false));
    }

    public static void status(Label label, int color) {
        label.textStyle(style -> style.fontSize(9).textColor(color).textShadow(false));
    }

    public static void input(TextField field, Component placeholder) {
        field.style(style -> style.background(panelTexture(0xFF10161D, BORDER)));
        field.textFieldStyle(style -> style
                .fontSize(10)
                .textColor(TEXT)
                .cursorColor(ACCENT)
                .errorColor(DANGER)
                .placeholder(placeholder)
                .focusOverlay(new ColorBorderTexture(1, ACCENT)));
    }

    public static void selector(Selector<?> selector) {
        selector.style(style -> style.background(panelTexture(0xFF10161D, BORDER)));
        selector.dialog.style(style -> style.background(panelTexture(SURFACE, BORDER)));
        selector.selectorStyle(style -> style
                .focusOverlay(new ColorBorderTexture(1, ACCENT))
                .maxItemCount(6)
                .showOverlay(true)
                .closeAfterSelect(true));
    }

    public static void button(Button button, ButtonKind kind) {
        int base;
        int hover;
        int pressed;
        int text;
        switch (kind) {
            case PRIMARY -> {
                base = ACCENT_DARK;
                hover = ACCENT;
                pressed = 0xFF1A5278;
                text = 0xFFFFFFFF;
            }
            case DANGER -> {
                base = 0xFF71383D;
                hover = DANGER;
                pressed = 0xFF54292D;
                text = 0xFFFFFFFF;
            }
            case QUIET -> {
                base = 0x00151B23;
                hover = 0xFF27323D;
                pressed = 0xFF11171E;
                text = MUTED;
            }
            default -> {
                base = ELEVATED;
                hover = 0xFF2B3946;
                pressed = 0xFF10161D;
                text = TEXT;
            }
        }
        button.buttonStyle(style -> style
                .baseTexture(panelTexture(base, BORDER))
                .hoverTexture(panelTexture(hover, kind == ButtonKind.PRIMARY ? ACCENT : BORDER))
                .pressedTexture(panelTexture(pressed, ACCENT_DARK)));
        button.textStyle(style -> style.fontSize(9).textColor(text).textShadow(false));
    }

    public static void holdActionButton(Button button, boolean active) {
        if (!active) {
            button(button, ButtonKind.QUIET);
            return;
        }
        button.buttonStyle(style -> style
                .baseTexture(panelTexture(0xFF5B3035, 0xFF774149))
                .hoverTexture(panelTexture(0xFF71383D, DANGER))
                .pressedTexture(panelTexture(0xFF71383D, DANGER)));
        button.textStyle(style -> style.fontSize(9).textColor(TEXT).textShadow(false));
    }

    public static void roomRow(Button button, int statusColor, boolean selected) {
        int base = selected ? 0xFF23384A : ELEVATED;
        int border = selected ? ACCENT : statusColor;
        button.buttonStyle(style -> style
                .baseTexture(panelTexture(base, border))
                .hoverTexture(panelTexture(0xFF293744, ACCENT))
                .pressedTexture(panelTexture(0xFF14222D, ACCENT_DARK)));
        button.textStyle(style -> style.fontSize(10).textColor(TEXT).textShadow(false));
    }

    public static void tab(Tab tab) {
        tab.tabStyle(style -> style
                .baseTexture(panelTexture(SURFACE, BORDER_SOFT))
                .hoverTexture(panelTexture(ELEVATED, BORDER))
                .selectedTexture(panelTexture(0xFF20374A, ACCENT)));
        tab.textStyle(style -> style.fontSize(9).textColor(TEXT).textShadow(false));
    }

    public static void slot(ItemSlot slot) {
        slot.style(style -> style.background(panelTexture(0xFF10161D, BORDER)));
        slot.slotStyle(style -> style
                .slotOverlay(new ColorBorderTexture(1, BORDER))
                .hoverOverlay(new ColorRectTexture(0x304BB3FD))
                .showItemTooltips(true));
    }

    public static IGuiTexture panelTexture(int fill, int border) {
        return IGuiTexture.group(new ColorRectTexture(fill), new ColorBorderTexture(1, border));
    }
}
