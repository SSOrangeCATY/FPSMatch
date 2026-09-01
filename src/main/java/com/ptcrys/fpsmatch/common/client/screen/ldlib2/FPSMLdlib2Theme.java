package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

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
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;

/** Shared visual language for FPSMatch LDLib2 work surfaces. */
public final class FPSMLdlib2Theme {
    public static final int BG = 0xF20D1110;
    public static final int SURFACE = 0xF2171D1C;
    public static final int ELEVATED = 0xF2202826;
    public static final int BORDER = 0xFF40504C;
    public static final int BORDER_SOFT = 0xFF2B3734;
    public static final int ACCENT = 0xFF53C9C2;
    public static final int ACCENT_DARK = 0xFF287C78;
    public static final int SUCCESS = 0xFF70CE96;
    public static final int WARNING = 0xFFF0BF57;
    public static final int DANGER = 0xFFE36B5F;
    public static final int TEXT = 0xFFF0F3EC;
    public static final int MUTED = 0xFF98A59F;
    public static final int DISABLED = 0xFF68746F;
    public static final int SETTINGS_CATEGORY = 0xFF293532;
    public static final int SETTINGS_ENTRY = 0xFF121917;
    public static final int SETTINGS_CATEGORY_TEXT = 0xFFE4ECE7;
    public static final int SETTINGS_ENTRY_TEXT = 0xFFBEC9C3;
    public static final int HOLD_ACTION_PROGRESS = 0xFFE36B5F;
    public static final int FOCUS_RING_WIDTH = 2;
    /** Shared map-system geometry tokens: restrained measurement lines, not decoration. */
    public static final int GRID_LINE = 0x183A5D55;
    public static final int SYSTEM_LABEL = 0xFF86A49C;
    public static final int MAP_IDENTITY = 0xFFC6D7D0;
    public static final int FOCUS_ACCENT = ACCENT;
    private static final IGuiTexture FOCUS_RING =
            new ColorBorderTexture(FOCUS_RING_WIDTH, ACCENT);

    private FPSMLdlib2Theme() {
    }

    public enum ButtonKind {
        PRIMARY, SECONDARY, DANGER, QUIET
    }

    public static void root(UIElement element) {
        element.style(style -> style.background(panelTexture(BG, BORDER_SOFT)));
    }

    /** Compact technical label used for breadcrumbs, coordinates and map identity lines. */
    public static void systemLabel(Label label) {
        label.textStyle(style -> style.fontSize(7).textColor(SYSTEM_LABEL).textShadow(false));
    }

    /** Primary map identity line; deliberately readable above the technical annotation tier. */
    public static void mapIdentity(Label label) {
        label.textStyle(style -> style.fontSize(9).textColor(MAP_IDENTITY).textShadow(false));
    }

    public static void panel(UIElement element) {
        element.style(style -> style.background(panelTexture(SURFACE, BORDER)));
    }

    /**
     * Opaque list surface shared by every map-room roster and settings list. LDLib2's default
     * viewport texture is purple on some Forge versions, so all three scroll layers are explicit.
     */
    public static void virtualScroller(VirtualScrollerView<?> scroller) {
        scroller.style(style -> style.background(panelTexture(0xFF111A18, BORDER_SOFT)));
        scroller.viewPort(element -> element.style(style ->
                style.background(new ColorRectTexture(0xFF111A18))));
        scroller.viewContainer(element -> element.style(style ->
                style.background(new ColorRectTexture(0x00111A18))));
    }

    public static void elevated(UIElement element) {
        element.style(style -> style.background(panelTexture(ELEVATED, BORDER)));
    }

    public static void statusSurface(UIElement element, int tone) {
        element.style(style -> style.background(panelTexture(SURFACE, tone)));
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

    /** Applies one truthful visual, pointer, and focus state to an actionable button. */
    public static void buttonState(Button button, ButtonKind kind, boolean enabled) {
        button.setActive(enabled);
        button.setAllowHitTest(enabled);
        button.setFocusable(enabled);
        if (enabled) {
            button(button, kind);
            return;
        }
        if (button.isFocused()) {
            button.blur();
        }
        IGuiTexture disabled = panelTexture(0xFF171D24, BORDER_SOFT);
        button.buttonStyle(style -> style
                .baseTexture(disabled)
                .hoverTexture(disabled)
                .pressedTexture(disabled));
        button.textStyle(style -> style.fontSize(9).textColor(DISABLED).textShadow(false));
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

    public static void drawFocusRing(UIElement element, GUIContext context) {
        if (!element.isFocused()) {
            return;
        }
        context.drawTexture(
                FOCUS_RING,
                element.getPositionX(),
                element.getPositionY(),
                element.getSizeWidth(),
                element.getSizeHeight());
    }

    public static IGuiTexture panelTexture(int fill, int border) {
        return IGuiTexture.group(new ColorRectTexture(fill), new ColorBorderTexture(1, border));
    }
}
