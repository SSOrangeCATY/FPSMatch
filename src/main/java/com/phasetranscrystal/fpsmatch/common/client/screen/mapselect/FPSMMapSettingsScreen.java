package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FPSMMapSettingsScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_TOP = 58;
    private static final int PANEL_BOTTOM_PADDING = 60;
    private static final int LIST_TOP_OFFSET = 10;
    private static final int LIST_BOTTOM_PADDING = 10;
    private static final int ROW_HEIGHT = 25;
    private static final int FIELD_WIDTH = 126;
    private static final int APPLY_BUTTON_WIDTH = 62;
    private static final int TOGGLE_BUTTON_WIDTH = FIELD_WIDTH + APPLY_BUTTON_WIDTH + 4;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<EditBox> valueFields = new ArrayList<>();
    private final List<Button> applyButtons = new ArrayList<>();
    private final List<Button> toggleButtons = new ArrayList<>();
    private final List<Integer> rowBaseY = new ArrayList<>();
    private Button backButton;
    private int scrollOffset;

    public FPSMMapSettingsScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.settings.title"));
        this.detail = detail;
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        valueFields.clear();
        applyButtons.clear();
        toggleButtons.clear();
        rowBaseY.clear();

        int left = panelLeft();
        int right = left + panelWidth();
        int valueX = right - FIELD_WIDTH - 82;
        int buttonX = right - 74;

        for (int i = 0; i < detail.settings().size(); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            int baseY = listTop() + i * ROW_HEIGHT;
            rowBaseY.add(baseY);

            if (setting.type() == MapRoomSettingInfo.SettingType.BOOLEAN) {
                valueFields.add(null);
                applyButtons.add(null);

                Button toggleButton = createToggleButton(Boolean.parseBoolean(setting.value()), valueX, baseY + 2,
                        button -> toggleSetting(setting, button));
                toggleButton.active = setting.editable();
                addRenderableWidget(toggleButton);
                toggleButtons.add(toggleButton);
            } else {
                EditBox field = new EditBox(font, valueX, baseY + 3, FIELD_WIDTH, 18, Component.translatable(setting.translationKey()));
                field.setMaxLength(1024);
                field.setValue(setting.value());
                field.setEditable(setting.editable());
                addRenderableWidget(field);
                valueFields.add(field);

                Button applyButton = Button.builder(Component.translatable("gui.fpsm.map_select.apply"), button -> applySetting(setting, field))
                        .bounds(buttonX, baseY + 2, APPLY_BUTTON_WIDTH, FPSMGuiTheme.BUTTON_HEIGHT)
                        .build();
                applyButton.active = setting.editable();
                addRenderableWidget(applyButton);
                applyButtons.add(applyButton);

                toggleButtons.add(null);
            }
        }

        backButton = createBackButton(button -> onClose());
        addRenderableWidget(backButton);
    }

    @Override
    public void tick() {
        super.tick();
        valueFields.stream().filter(Objects::nonNull).forEach(EditBox::tick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);

        drawScreenTitle(graphics, title, detail != null ? Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()) : null, 12);

        int visibleRows = visibleRows();
        int maxScroll = maxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int left = panelLeft();
        int right = left + panelWidth();
        int panelTop = PANEL_TOP;
        int panelBottom = panelBottom();
        int listTop = listTop();
        int listBottom = listBottom();
        drawListBackground(graphics, left, panelTop, right, panelBottom);

        Component tooltip = null;
        graphics.enableScissor(left + 8, listTop, right - 8, listBottom);

        for (int i = 0; i < Math.min(valueFields.size(), detail.settings().size()); i++) {
            int targetY = rowBaseY.get(i) - scrollOffset * ROW_HEIGHT;
            boolean visible = targetY + ROW_HEIGHT > listTop && targetY < listBottom;

            EditBox field = valueFields.get(i);
            Button applyButton = applyButtons.get(i);
            Button toggleButton = toggleButtons.get(i);

            if (field != null) {
                field.visible = visible;
                field.setY(targetY + 3);
            }
            if (applyButton != null) {
                applyButton.visible = visible;
                applyButton.setY(targetY + 2);
            }
            if (toggleButton != null) {
                toggleButton.visible = visible;
                toggleButton.setY(targetY + 2);
            }

            if (!visible) continue;

            MapRoomSettingInfo setting = detail.settings().get(i);
            Component settingName = Component.translatable(setting.translationKey());
            boolean hovered = mouseX >= left + 10 && mouseX <= right - 10 && mouseY >= targetY && mouseY <= targetY + ROW_HEIGHT;
            drawRowBackground(graphics, left + 10, targetY, right - 10, targetY + ROW_HEIGHT - 1, false, hovered, !setting.editable());
            drawClippedString(graphics, settingName, left + 18, targetY + 8,
                    setting.editable() ? FPSMGuiTheme.TEXT_HIGHLIGHT : FPSMGuiTheme.TEXT_DISABLED, 136);
            drawClippedString(graphics, Component.translatable("gui.fpsm.map_select.setting.default", setting.defaultValue()),
                    left + 158, targetY + 8, FPSMGuiTheme.TEXT_SUB, 130);
            if (field != null) field.render(graphics, mouseX, mouseY, partialTick);
            if (applyButton != null) applyButton.render(graphics, mouseX, mouseY, partialTick);
            if (toggleButton != null) toggleButton.render(graphics, mouseX, mouseY, partialTick);

            if (hovered && mouseX >= left + 18 && mouseX <= left + 154) {
                tooltip = Component.translatable(setting.translationKey() + ".desc");
            }
        }
        graphics.disableScissor();

        if (detail.settings().isEmpty()) {
            drawEmptyState(graphics, Component.translatable("gui.fpsm.map_select.settings.empty"), width / 2, listTop + 42);
        } else {
            drawScrollBar(graphics, right - 8, listTop, listBottom - listTop, scrollOffset, maxScroll, detail.settings().size(), visibleRows);
        }

        if (backButton != null) {
            backButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (tooltip != null) {
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scrollOffset -= (int) scrollY;
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox field : valueFields) {
            if (field != null && field.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox field : valueFields) {
            if (field != null && field.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applySetting(MapRoomSettingInfo setting, EditBox field) {
        FPSMatch.sendToServer(new MapRoomSettingsC2SPacket(detail.summary().gameType(), detail.summary().mapName(), setting.name(), field.getValue()));
    }

    private void toggleSetting(MapRoomSettingInfo setting, Button button) {
        boolean newValue = !Boolean.parseBoolean(setting.value());
        button.setMessage(toggleLabel(newValue));
        FPSMatch.sendToServer(new MapRoomSettingsC2SPacket(detail.summary().gameType(), detail.summary().mapName(), setting.name(), String.valueOf(newValue)));
    }

    private Button createToggleButton(boolean value, int x, int y, Button.OnPress onPress) {
        return Button.builder(toggleLabel(value), onPress)
                .bounds(x, y, TOGGLE_BUTTON_WIDTH, FPSMGuiTheme.BUTTON_HEIGHT)
                .build();
    }

    private Component toggleLabel(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, width - 24);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelBottom() {
        return height - PANEL_BOTTOM_PADDING;
    }

    private int listTop() {
        return PANEL_TOP + LIST_TOP_OFFSET;
    }

    private int listBottom() {
        return panelBottom() - LIST_BOTTOM_PADDING;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, detail.settings().size() - visibleRows());
    }
}
