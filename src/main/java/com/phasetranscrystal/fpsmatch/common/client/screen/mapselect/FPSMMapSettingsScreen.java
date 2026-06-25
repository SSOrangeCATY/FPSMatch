package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class FPSMMapSettingsScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int GUI_SHADOW_COLOR = 0x80000000;
    private static final int GUI_MAIN_BACKGROUND = 0xFF444444;
    private static final int GUI_INNER_BORDER = 0xFF666666;
    private static final int GUI_OUTER_BORDER = 0xFF222222;
    private static final int GUI_PADDING = 4;

    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = 62;

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

            EditBox field = new EditBox(font, left + 176, baseY + 5, 150, 18, Component.translatable(setting.translationKey()));
            field.setMaxLength(128);
            field.setValue(setting.value());
            field.setEditable(setting.editable());
            addRenderableWidget(field);
            valueFields.add(field);

            Button applyButton = Button.builder(Component.translatable("gui.fpsm.map_select.apply"), button -> applySetting(setting, field))
                    .bounds(left + 334, baseY + 4, 70, 20)
                    .build();
            applyButton.active = setting.editable();
            addRenderableWidget(applyButton);
            applyButtons.add(applyButton);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 52, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        renderMultiLayerBackground(graphics);

        // 鏍囬
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        if (detail != null) {
            graphics.centeredText(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 26, 0xFFB8D4E3);
        }

        int contentHeight = height - LIST_TOP - 62;
        int visibleRows = Math.max(1, contentHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.settings().size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int left = width / 2 - 210;
        int right = width / 2 + 210;
        int panelTop = LIST_TOP - 2;
        int panelBottom = height - 60;
        graphics.fill(left - 6, panelTop, right + 6, panelBottom, 0x77000000);
        graphics.fill(left - 6, panelTop, right + 6, panelTop + 1, 0xFF666666);
        graphics.fill(left - 6, panelBottom - 1, right + 6, panelBottom, 0xFF666666);

        // 裁剪区域
        graphics.enableScissor(left - 8, panelTop + 2, right + 8, panelBottom - 2);

        // 重定位所有组件并渲染标签
        for (int i = 0; i < Math.min(valueFields.size(), detail.settings().size()); i++) {
            int targetY = rowBaseY.get(i) - scrollOffset * ROW_HEIGHT;
            boolean visible = targetY + ROW_HEIGHT > panelTop && targetY < panelBottom;

            EditBox field = valueFields.get(i);
            field.visible = visible;
            field.setY(targetY + 5);

            Button applyButton = applyButtons.get(i);
            applyButton.visible = visible;
            applyButton.setY(targetY + 4);

            MapRoomSettingInfo setting = detail.settings().get(i);
            Component settingName = Component.translatable(setting.translationKey());
            graphics.text(font, settingName, left, targetY + 10, setting.editable() ? 0xFFE6F2FF : 0xFF8F9AA3, false);
            graphics.text(font, Component.translatable("gui.fpsm.map_select.setting.default", setting.defaultValue()), left + 82, targetY + 10, 0xFFB8D4E3, false);

            // 鎮诞鎻愮ず
            if (mouseX >= left - 2 && mouseX <= left + 80 && mouseY >= targetY && mouseY <= targetY + ROW_HEIGHT) {
                graphics.setTooltipForNextFrame(font, Component.translatable(setting.translationKey() + ".desc"), mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        renderScrollBar(graphics, right, panelTop + 2, panelBottom - panelTop - 4, scrollOffset, maxScroll, detail.settings().size(), visibleRows);

        if (detail.settings().isEmpty()) {
            graphics.centeredText(font, Component.translatable("gui.fpsm.map_select.settings.empty"), width / 2, LIST_TOP + 32, 0xFFAAAAAA);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMultiLayerBackground(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.fill(2, 2, width + 2, height + 2, GUI_SHADOW_COLOR);
        guiGraphics.fill(0, 0, width, 1, GUI_OUTER_BORDER);
        guiGraphics.fill(0, height - 1, width, height, GUI_OUTER_BORDER);
        guiGraphics.fill(0, 1, 1, height - 1, GUI_OUTER_BORDER);
        guiGraphics.fill(width - 1, 1, width, height - 1, GUI_OUTER_BORDER);
        guiGraphics.fill(1, 1, width - 1, height - 1, GUI_MAIN_BACKGROUND);
        guiGraphics.fill(1 + GUI_PADDING, 1 + GUI_PADDING, width - 1 - GUI_PADDING, 1 + GUI_PADDING + 1, GUI_INNER_BORDER);
        guiGraphics.fill(1 + GUI_PADDING, height - 1 - GUI_PADDING - 1, width - 1 - GUI_PADDING, height - 1 - GUI_PADDING, GUI_INNER_BORDER);
        guiGraphics.fill(1 + GUI_PADDING, 1 + GUI_PADDING + 1, 1 + GUI_PADDING + 1, height - 1 - GUI_PADDING - 1, GUI_INNER_BORDER);
        guiGraphics.fill(width - 1 - GUI_PADDING - 1, 1 + GUI_PADDING + 1, width - 1 - GUI_PADDING, height - 1 - GUI_PADDING - 1, GUI_INNER_BORDER);
    }

    private void renderScrollBar(GuiGraphicsExtractor graphics, int barX, int barY, int barHeight, int scroll, int maxScroll, int totalItems, int visibleItems) {
        if (maxScroll <= 0) return;
        int barWidth = 4;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x33000000);
        int thumbSize = Math.max(10, barHeight * visibleItems / Math.max(1, totalItems));
        int thumbY = barY + scroll * (barHeight - thumbSize) / Math.max(1, maxScroll);
        graphics.fill(barX, thumbY, barX + barWidth, thumbY + thumbSize, 0x88FFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = height - LIST_TOP - 62;
        int visibleRows = Math.max(1, contentHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, detail.settings().size() - visibleRows);
        scrollOffset -= (int) scrollY;
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (EditBox field : valueFields) {
            if (field.keyPressed(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (EditBox field : valueFields) {
            if (field.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 将滚动后的点击位置映射回组件实际位置
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applySetting(MapRoomSettingInfo setting, EditBox field) {
        FPSMatch.sendToServer(new MapRoomSettingsC2SPacket(detail.summary().gameType(), detail.summary().mapName(), setting.name(), field.getValue()));
    }
}
