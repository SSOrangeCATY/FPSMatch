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

public class FPSMMapSettingsScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 62;
    private static final int EDIT_BOX_WIDTH = 180;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<EditBox> valueFields = new ArrayList<>();
    private final List<Button> applyButtons = new ArrayList<>();
    private final List<Integer> rowBaseY = new ArrayList<>();
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
        rowBaseY.clear();

        int left = width / 2 - 210;

        for (int i = 0; i < detail.settings().size(); i++) {
            MapRoomSettingInfo setting = detail.settings().get(i);
            int baseY = LIST_TOP + i * ROW_HEIGHT;
            rowBaseY.add(baseY);

            EditBox field = new EditBox(font, left + 176, baseY + 3, EDIT_BOX_WIDTH, 18, Component.translatable(setting.translationKey()));
            field.setMaxLength(128);
            field.setValue(setting.value());
            field.setEditable(setting.editable());
            addRenderableWidget(field);
            valueFields.add(field);

            Button applyButton = createSmallButton(Component.translatable("gui.fpsm.map_select.apply"), left + 176 + EDIT_BOX_WIDTH + 6, baseY + 2,
                    button -> applySetting(setting, field));
            applyButton.active = setting.editable();
            addRenderableWidget(applyButton);
            applyButtons.add(applyButton);
        }

        addRenderableWidget(createBackButton(button -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        valueFields.forEach(EditBox::tick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);

        // 标题
        graphics.drawCenteredString(font, title, width / 2, 12, FPSMGuiTheme.TEXT_TITLE);
        if (detail != null) {
            graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 26, FPSMGuiTheme.TEXT_SUB);
        }

        int contentHeight = height - LIST_TOP - 62;
        int visibleRows = Math.max(1, contentHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.settings().size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        // 内容区背景（统一）
        int left = width / 2 - 210;
        int right = width / 2 + 210;
        int panelTop = LIST_TOP - 2;
        int panelBottom = height - 60;
        drawListBackground(graphics, left - 6, panelTop, right + 6, panelBottom);

        // 裁剪区域
        graphics.enableScissor(left - 8, panelTop + 2, right + 8, panelBottom - 2);

        // 重定位所有组件并渲染标签
        for (int i = 0; i < Math.min(valueFields.size(), detail.settings().size()); i++) {
            int targetY = rowBaseY.get(i) - scrollOffset * ROW_HEIGHT;
            boolean visible = targetY + ROW_HEIGHT > panelTop && targetY < panelBottom;

            EditBox field = valueFields.get(i);
            field.visible = visible;
            field.setY(targetY + 3);

            Button applyButton = applyButtons.get(i);
            applyButton.visible = visible;
            applyButton.setY(targetY + 2);

            MapRoomSettingInfo setting = detail.settings().get(i);
            Component settingName = Component.translatable(setting.translationKey());
            graphics.drawString(font, settingName, left, targetY + 8, setting.editable() ? FPSMGuiTheme.TEXT_HIGHLIGHT : FPSMGuiTheme.TEXT_DISABLED, false);
            graphics.drawString(font, Component.translatable("gui.fpsm.map_select.setting.default", setting.defaultValue()), left + 82, targetY + 8, FPSMGuiTheme.TEXT_SUB, false);

            // 悬浮提示
            if (mouseX >= left - 2 && mouseX <= left + 80 && mouseY >= targetY && mouseY <= targetY + ROW_HEIGHT) {
                graphics.renderTooltip(font, Component.translatable(setting.translationKey() + ".desc"), mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        // 滚动条
        drawScrollBar(graphics, right, panelTop + 2, panelBottom - panelTop - 4, scrollOffset, maxScroll, detail.settings().size(), visibleRows);

        if (detail.settings().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.settings.empty"), width / 2, LIST_TOP + 32, FPSMGuiTheme.TEXT_MUTED);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int contentHeight = height - LIST_TOP - 62;
        int visibleRows = Math.max(1, contentHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.settings().size() - visibleRows);
        scrollOffset -= (int) scrollY;
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        return true;
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
}
