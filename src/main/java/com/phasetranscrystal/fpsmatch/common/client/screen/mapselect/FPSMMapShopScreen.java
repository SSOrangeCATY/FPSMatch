package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FPSMMapShopScreen extends Screen implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;

    public FPSMMapShopScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_shop.title"));
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
        int left = width / 2 - PANEL_WIDTH / 2;
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.editableShops().size(), visibleRows()); i++) {
            EditableShopInfo shop = detail.editableShops().get(i);
            addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_shop.edit"), button -> openEditor(shop))
                    .bounds(left + 258, y + 3, 78, 20)
                    .build());
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 52, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 24, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 48, 0xFFB8D4E3);
        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = Math.min(height - 84, LIST_TOP + visibleRows() * ROW_HEIGHT);
        graphics.fill(left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6, 0x77000000);
        if (detail.editableShops().isEmpty()) {
            graphics.centeredText(font, Component.translatable("gui.fpsm.map_shop.unsupported"), width / 2, LIST_TOP + 32, 0xFFAAAAAA);
        }
        int y = LIST_TOP;
        for (int i = 0; i < Math.min(detail.editableShops().size(), visibleRows()); i++) {
            EditableShopInfo shop = detail.editableShops().get(i);
            graphics.text(font, Component.literal(shop.displayName()), left, y + 8, 0xFFE6F2FF, false);
            graphics.text(font, Component.literal(shop.teamName()), left + 132, y + 8, 0xFF74E084, false);
            y += ROW_HEIGHT;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openEditor(EditableShopInfo shop) {
        FPSMatch.sendToServer(new OpenShopEditorC2SPacket(shop.gameType(), shop.mapName(), shop.teamName()));
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 92) / ROW_HEIGHT);
    }
}
