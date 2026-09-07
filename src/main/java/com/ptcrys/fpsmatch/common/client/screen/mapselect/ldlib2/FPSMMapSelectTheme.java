package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;

/** Visual tokens scoped to the map-selection product flow. */
public final class FPSMMapSelectTheme {
    public static final int BG = 0xF20B0D10;
    public static final int SURFACE = 0xF2181C20;
    public static final int ELEVATED = 0xF223282D;
    public static final int BORDER = 0xFF4A555D;
    public static final int BORDER_SOFT = 0xFF2D353B;
    public static final int ACCENT = 0xFF35D3CF;
    public static final int ACCENT_DARK = 0xFF176F70;
    public static final int SUCCESS = 0xFF72D69C;
    public static final int WARNING = 0xFFFFC857;
    public static final int DANGER = 0xFFFF6B61;
    public static final int TEXT = 0xFFF3F5F2;
    public static final int MUTED = 0xFFAAB3B8;
    public static final int DISABLED = 0xFF747D82;
    public static final int GRID_LINE = 0x162E3A40;
    public static final int SYSTEM_LABEL = 0xFF94A2A8;
    public static final int MAP_IDENTITY = 0xFFD5DEDF;
    public static final int SETTINGS_CATEGORY = 0xFF252B30;
    public static final int SETTINGS_ENTRY = 0xFF13171A;
    public static final int SETTINGS_CATEGORY_TEXT = 0xFFE8ECEA;
    public static final int SETTINGS_ENTRY_TEXT = 0xFFC4CBCE;
    public static final int HOLD_ACTION_PROGRESS = DANGER;
    public static final int FOCUS_RING_WIDTH = 2;
    public static final int FOCUS_ACCENT = ACCENT;

    private static final IGuiTexture FOCUS_RING =
            new ColorBorderTexture(FOCUS_RING_WIDTH, FOCUS_ACCENT);

    private FPSMMapSelectTheme() {
    }

    public enum ButtonKind {
        PRIMARY, SECONDARY, DANGER, QUIET
    }

    public static void root(UIElement element) {
        element.style(style -> style.background(new ColorRectTexture(BG)));
    }

    /** Small but readable navigation and factual metadata. */
    public static void systemLabel(Label label) {
        label.textStyle(style -> style.fontSize(8).textColor(SYSTEM_LABEL).textShadow(false));
    }

    public static void mapIdentity(Label label) {
        label.textStyle(style -> style.fontSize(11).textColor(MAP_IDENTITY).textShadow(false));
    }

    public static void panel(UIElement element) {
        element.style(style -> style.background(panelTexture(SURFACE, BORDER_SOFT)));
    }

    public static void elevated(UIElement element) {
        element.style(style -> style.background(panelTexture(ELEVATED, BORDER)));
    }

    public static void preview(UIElement element) {
        element.style(style -> style.background(panelTexture(0xFF101316, BORDER)));
    }

    public static void statusSurface(UIElement element, int tone) {
        element.style(style -> style.background(panelTexture(0xFF14191D, tone)));
    }

    public static void virtualScroller(VirtualScrollerView<?> scroller) {
        scroller.style(style -> style.background(new ColorRectTexture(0xFF101417)));
        scroller.viewPort(element -> element.style(style ->
                style.background(new ColorRectTexture(0xFF101417))));
        scroller.viewContainer(element -> element.style(style ->
                style.background(new ColorRectTexture(0x00101417))));
    }

    public static void settingsCategory(UIElement element) {
        element.style(style -> style.background(panelTexture(SETTINGS_CATEGORY, BORDER)));
    }

    public static void settingsEntry(UIElement element) {
        element.style(style -> style.background(panelTexture(SETTINGS_ENTRY, BORDER_SOFT)));
    }

    public static void settingsCategoryToggle(Toggle toggle) {
        toggle.style(style -> style.background(panelTexture(SETTINGS_ENTRY, BORDER_SOFT)));
        toggle.toggleStyle(style -> style
                .baseTexture(panelTexture(SETTINGS_ENTRY, BORDER_SOFT))
                .hoverTexture(panelTexture(ELEVATED, BORDER))
                .unmarkTexture(panelTexture(0xFF0E1114, BORDER))
                .markTexture(IGuiTexture.group(
                        panelTexture(ACCENT_DARK, ACCENT), Icons.CHECK_SPRITE)));
        toggle.toggleLabel(label -> label.textStyle(style -> style
                .fontSize(9).textColor(SETTINGS_ENTRY_TEXT).textShadow(false)));
    }

    public static void settingsScroller(ScrollerView scroller) {
        scroller.style(style -> style.background(new ColorRectTexture(0xFF101417)));
        scroller.viewPort(element -> element.style(style ->
                style.background(new ColorRectTexture(0xFF101417))));
        scroller.viewContainer(element -> element.style(style ->
                style.background(new ColorRectTexture(0xFF101417))));
    }

    public static void title(Label label) {
        label.textStyle(style -> style.fontSize(16).textColor(TEXT).textShadow(false));
    }

    public static void sectionTitle(Label label) {
        label.textStyle(style -> style.fontSize(11).textColor(TEXT).textShadow(false));
    }

    public static void body(Label label) {
        label.textStyle(style -> style.fontSize(10).textColor(TEXT).lineSpacing(2).textShadow(false));
    }

    public static void muted(Label label) {
        label.textStyle(style -> style.fontSize(9).textColor(MUTED).textShadow(false));
    }

    public static void status(Label label, int color) {
        label.textStyle(style -> style.fontSize(9).textColor(color).textShadow(false));
    }

    public static void input(TextField field, Component placeholder) {
        field.style(style -> style.background(panelTexture(0xFF101417, BORDER)));
        field.textFieldStyle(style -> style
                .fontSize(10)
                .textColor(TEXT)
                .cursorColor(ACCENT)
                .errorColor(DANGER)
                .placeholder(placeholder)
                .focusOverlay(new ColorBorderTexture(2, FOCUS_ACCENT)));
    }

    public static void selector(Selector<?> selector) {
        selector.style(style -> style.background(panelTexture(0xFF101417, BORDER)));
        selector.dialog.style(style -> style.background(panelTexture(SURFACE, BORDER)));
        selector.selectorStyle(style -> style
                .focusOverlay(new ColorBorderTexture(2, FOCUS_ACCENT))
                .maxItemCount(7)
                .showOverlay(true)
                .closeAfterSelect(true));
    }

    public static void button(Button button, ButtonKind kind) {
        int base;
        int hover;
        int pressed;
        int border;
        int text;
        switch (kind) {
            case PRIMARY -> {
                base = ACCENT_DARK;
                hover = 0xFF218E8E;
                pressed = 0xFF115657;
                border = ACCENT;
                text = 0xFFFFFFFF;
            }
            case DANGER -> {
                base = 0xFF542E31;
                hover = 0xFF76383C;
                pressed = 0xFF3D2427;
                border = DANGER;
                text = 0xFFFFFFFF;
            }
            case QUIET -> {
                base = 0x00181C20;
                hover = 0xFF292F34;
                pressed = 0xFF15191D;
                border = BORDER_SOFT;
                text = MUTED;
            }
            default -> {
                base = ELEVATED;
                hover = 0xFF30373D;
                pressed = 0xFF171C20;
                border = BORDER;
                text = TEXT;
            }
        }
        button.buttonStyle(style -> style
                .baseTexture(panelTexture(base, border))
                .hoverTexture(panelTexture(hover, kind == ButtonKind.QUIET ? BORDER : ACCENT))
                .pressedTexture(panelTexture(pressed, ACCENT_DARK)));
        button.textStyle(style -> style.fontSize(10).textColor(text).textShadow(false));
    }

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
        IGuiTexture disabled = panelTexture(0xFF171B1E, BORDER_SOFT);
        button.buttonStyle(style -> style
                .baseTexture(disabled)
                .hoverTexture(disabled)
                .pressedTexture(disabled));
        button.textStyle(style -> style.fontSize(10).textColor(DISABLED).textShadow(false));
    }

    public static void holdActionButton(Button button, boolean active) {
        button(button, active ? ButtonKind.DANGER : ButtonKind.QUIET);
    }

    public static void roomRow(Button button, int statusColor, boolean selected) {
        int base = selected ? 0xFF243136 : 0xFF171C20;
        int border = selected ? ACCENT : BORDER_SOFT;
        button.buttonStyle(style -> style
                .baseTexture(panelTexture(base, border))
                .hoverTexture(panelTexture(0xFF252D32, selected ? ACCENT : statusColor))
                .pressedTexture(panelTexture(0xFF12191D, ACCENT_DARK)));
        button.textStyle(style -> style.fontSize(10).textColor(TEXT).textShadow(false));
    }

    public static void tab(Tab tab) {
        tab.tabStyle(style -> style
                .baseTexture(panelTexture(SURFACE, BORDER_SOFT))
                .hoverTexture(panelTexture(ELEVATED, BORDER))
                .selectedTexture(panelTexture(0xFF213437, ACCENT)));
        tab.textStyle(style -> style.fontSize(10).textColor(TEXT).textShadow(false));
    }

    public static void slot(ItemSlot slot) {
        slot.style(style -> style.background(panelTexture(0xFF101417, BORDER)));
        slot.slotStyle(style -> style
                .slotOverlay(new ColorBorderTexture(1, BORDER))
                .hoverOverlay(new ColorRectTexture(0x3035D3CF))
                .showItemTooltips(true));
    }

    public static void drawFocusRing(UIElement element, GUIContext context) {
        if (element.isFocused()) {
            context.drawTexture(
                    FOCUS_RING,
                    element.getPositionX(),
                    element.getPositionY(),
                    element.getSizeWidth(),
                    element.getSizeHeight());
        }
    }

    public static IGuiTexture panelTexture(int fill, int border) {
        return IGuiTexture.group(new ColorRectTexture(fill), new ColorBorderTexture(1, border));
    }
}
