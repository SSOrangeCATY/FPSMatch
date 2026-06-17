package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FPSMMapShopScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 380;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<Button> editButtons = new ArrayList<>();
    private ScrollableList list;

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
        editButtons.clear();
        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = height - 60;
        // 为每个商店创建编辑按钮（全部创建，超出可见范围的由 ScrollableList 控制可见性）
        for (int i = 0; i < detail.editableShops().size(); i++) {
            EditableShopInfo shop = detail.editableShops().get(i);
            // 初始 y 用占位，render 时由 list 重新定位
            Button editButton = createSmallButton(Component.translatable("gui.fpsm.map_shop.edit"), left + PANEL_WIDTH - 80, LIST_TOP + i * ROW_HEIGHT + 2,
                    button -> openEditor(shop));
            addRenderableWidget(editButton);
            editButtons.add(editButton);
        }
        addRenderableWidget(createBackButton(button -> onClose()));

        // 创建可滚动列表
        list = new ScrollableList(left, LIST_TOP, PANEL_WIDTH, bottom, ROW_HEIGHT, 0) {
            @Override
            public int totalItems() {
                return detail.editableShops().size();
            }

            @Override
            protected void renderRow(GuiGraphics graphics, int index, int rowTop, int mouseX, int mouseY) {
                EditableShopInfo shop = detail.editableShops().get(index);
                graphics.drawString(font, Component.literal(shop.displayName()), left + 8, rowTop + 8, FPSMGuiTheme.TEXT_HIGHLIGHT, false);
                graphics.drawString(font, Component.literal(shop.teamName()), left + 140, rowTop + 8, FPSMGuiTheme.ST_ONLINE, false);
                // 重新定位对应按钮
                Button btn = editButtons.get(index);
                btn.setX(left + PANEL_WIDTH - 80);
                btn.setY(rowTop + 2);
                btn.visible = true;
            }
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, FPSMGuiTheme.TEXT_TITLE);
        graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 48, FPSMGuiTheme.TEXT_SUB);

        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = height - 60;
        // 列表面板背景（统一）
        drawListBackground(graphics, left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6);

        if (detail.editableShops().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_shop.unsupported"), width / 2, LIST_TOP + 32, FPSMGuiTheme.TEXT_MUTED);
        } else {
            // 先隐藏所有按钮，由 list.render 重新设置可见性
            editButtons.forEach(b -> b.visible = false);
            list.render(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (list != null && list.handleMouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openEditor(EditableShopInfo shop) {
        FPSMatch.sendToServer(new OpenShopEditorC2SPacket(shop.gameType(), shop.mapName(), shop.teamName()));
    }
}
