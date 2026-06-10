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

import java.util.ArrayList;
import java.util.List;

public class FPSMMapSettingsScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 420;
    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<EditBox> valueFields = new ArrayList<>();

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
        int left = width / 2 - PANEL_WIDTH / 2;
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.settings().size(), visibleRows()); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            EditBox field = new EditBox(font, left + 176, y + 5, 150, 18, Component.literal(setting.name()));
            field.setMaxLength(128);
            field.setValue(setting.value());
            field.setEditable(setting.editable());
            addRenderableWidget(field);
            valueFields.add(field);
            Button applyButton = Button.builder(Component.translatable("gui.fpsm.map_select.apply"), button -> applySetting(setting, field))
                    .bounds(left + 334, y + 4, 70, 20)
                    .build();
            applyButton.active = setting.editable();
            addRenderableWidget(applyButton);
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 52, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        valueFields.forEach(EditBox::tick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 48, 0xFFB8D4E3);
        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = Math.min(height - 84, LIST_TOP + visibleRows() * ROW_HEIGHT);
        graphics.fill(left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6, 0x77000000);
        if (detail.settings().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.settings.empty"), width / 2, LIST_TOP + 32, 0xFFAAAAAA);
        }
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.settings().size(), visibleRows()); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            graphics.drawString(font, Component.literal(setting.name()), left, y + 10, setting.editable() ? 0xFFE6F2FF : 0xFF8F9AA3, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.setting.default", setting.defaultValue()), left + 82, y + 10, 0xFFB8D4E3, false);
            y += ROW_HEIGHT;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox field : valueFields) {
            if (field.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox field : valueFields) {
            if (field.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
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

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 92) / ROW_HEIGHT);
    }
}
