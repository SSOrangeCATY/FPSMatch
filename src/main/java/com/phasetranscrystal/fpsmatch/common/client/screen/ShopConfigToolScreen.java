package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapScreenBase;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ScrollableList;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.EditableShopInfo;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopConfigToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopConfigToolActionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店配置工具界面。
 * <p>
 * 顶部提供游戏类型和地图选择器，下方显示可编辑商店列表。
 * 点击商店行的"编辑"按钮直接打开已有的 EditorShopScreen。
 */
public class ShopConfigToolScreen extends FPSMMapScreenBase {
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_WIDTH = 420;

    private OpenShopConfigToolScreenS2CPacket data;
    private final List<Button> editButtons = new ArrayList<>();
    private ScrollableList list;

    public ShopConfigToolScreen(OpenShopConfigToolScreenS2CPacket data) {
        super(Component.translatable("gui.fpsm.shop_config.title"));
        this.data = data;
    }

    public void applyData(OpenShopConfigToolScreenS2CPacket data) {
        this.data = data;
        rebuildToolWidgets();
    }

    @Override
    protected void init() {
        rebuildToolWidgets();
    }

    private void rebuildToolWidgets() {
        clearWidgets();
        editButtons.clear();

        int left = width / 2 - PANEL_WIDTH / 2;
        int listTop = 86;
        int bottom = height - 60;

        // 顶部：游戏类型选择按钮
        Button typeButton = addRenderableWidget(Button.builder(selectedTypeLabel(), button -> cycleType())
                .bounds(left + 64, 30, 132, BUTTON_HEIGHT)
                .build());

        // 地图选择按钮
        Button mapButton = addRenderableWidget(Button.builder(selectedMapLabel(), button -> cycleMap())
                .bounds(left + 250, 30, 150, BUTTON_HEIGHT)
                .build());

        // 刷新按钮
        addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.shop_config.refresh"), button -> refresh())
                .bounds(left + PANEL_WIDTH - 64, 30, 64, BUTTON_HEIGHT)
                .build());

        // 为每个商店创建编辑按钮
        for (int i = 0; i < data.shops().size(); i++) {
            EditableShopInfo shop = data.shops().get(i);
            Button editButton = createSmallButton(Component.translatable("gui.fpsm.shop_config.edit"),
                    left + PANEL_WIDTH - 80, listTop + i * ROW_HEIGHT + 2,
                    button -> openEditor(shop));
            addRenderableWidget(editButton);
            editButtons.add(editButton);
        }

        // 返回按钮
        addRenderableWidget(createBackButton(button -> onClose()));

        // 创建可滚动列表
        list = new ScrollableList(left, listTop, PANEL_WIDTH, bottom, ROW_HEIGHT, 0) {
            @Override
            public int totalItems() {
                return data.shops().size();
            }

            @Override
            protected void renderRow(GuiGraphics graphics, int index, int rowTop, int mouseX, int mouseY) {
                EditableShopInfo shop = data.shops().get(index);
                boolean hovered = mouseX >= left && mouseX <= left + PANEL_WIDTH && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
                drawRowBackground(graphics, left, rowTop, left + PANEL_WIDTH, rowTop + ROW_HEIGHT, false, hovered, false);
                drawClippedString(graphics, Component.literal(shop.displayName()), left + 8, rowTop + 8, FPSMGuiTheme.TEXT_HIGHLIGHT, 140);
                drawClippedString(graphics, Component.literal(shop.teamName()), left + 160, rowTop + 8, FPSMGuiTheme.ST_ONLINE, 160);
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
        drawScreenTitle(graphics, title, null, 12);

        int left = width / 2 - PANEL_WIDTH / 2;

        // 选择器标签
        graphics.drawString(font, Component.translatable("gui.fpsm.shop_config.type"), left + 12, 36, FPSMGuiTheme.TEXT_SUB, false);
        graphics.drawString(font, Component.translatable("gui.fpsm.shop_config.map"), left + 214, 36, FPSMGuiTheme.TEXT_SUB, false);

        // 列表区域
        int listTop = 86;
        int bottom = height - 60;
        drawListBackground(graphics, left - 6, listTop - 6, left + PANEL_WIDTH + 6, bottom + 6);

        if (data.shops().isEmpty()) {
            String key = data.maps().isEmpty() ? "gui.fpsm.shop_config.no_maps" : "gui.fpsm.shop_config.empty";
            drawEmptyState(graphics, Component.translatable(key), width / 2, listTop + 42);
        } else {
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
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openEditor(EditableShopInfo shop) {
        FPSMatch.sendToServer(new OpenShopEditorC2SPacket(shop.gameType(), shop.mapName(), shop.teamName()));
    }

    private void cycleType() {
        List<String> types = data.maps().stream().map(OpenShopConfigToolScreenS2CPacket.MapEntry::gameType).distinct().toList();
        if (types.isEmpty()) return;
        int index = types.indexOf(data.selectedType());
        String nextType = types.get(index < 0 ? 0 : (index + 1) % types.size());
        String nextMap = data.maps().stream()
                .filter(map -> map.gameType().equals(nextType))
                .findFirst()
                .map(OpenShopConfigToolScreenS2CPacket.MapEntry::mapName)
                .orElse("");
        select(nextType, nextMap);
    }

    private void cycleMap() {
        List<OpenShopConfigToolScreenS2CPacket.MapEntry> maps = data.maps().stream()
                .filter(map -> map.gameType().equals(data.selectedType()))
                .toList();
        if (maps.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < maps.size(); i++) {
            if (maps.get(i).mapName().equals(data.selectedMap())) {
                index = i;
                break;
            }
        }
        OpenShopConfigToolScreenS2CPacket.MapEntry next = maps.get(index < 0 ? 0 : (index + 1) % maps.size());
        select(next.gameType(), next.mapName());
    }

    private void refresh() {
        FPSMatch.sendToServer(new ShopConfigToolActionC2SPacket(
                ShopConfigToolActionC2SPacket.Action.REFRESH,
                data.selectedType(),
                data.selectedMap()
        ));
    }

    private void select(String gameType, String mapName) {
        FPSMatch.sendToServer(new ShopConfigToolActionC2SPacket(
                ShopConfigToolActionC2SPacket.Action.SELECT,
                gameType,
                mapName
        ));
    }

    private Component selectedTypeLabel() {
        return Component.literal(data.selectedType().isBlank() ? "-" : data.selectedType());
    }

    private Component selectedMapLabel() {
        if (data.selectedMap().isBlank()) {
            return Component.literal("-");
        }
        return data.maps().stream()
                .filter(map -> map.gameType().equals(data.selectedType()) && map.mapName().equals(data.selectedMap()))
                .findFirst()
                .map(map -> Component.literal(map.displayName().isBlank() ? map.mapName() : map.displayName()))
                .orElseGet(() -> Component.literal(data.selectedMap()));
    }
}
