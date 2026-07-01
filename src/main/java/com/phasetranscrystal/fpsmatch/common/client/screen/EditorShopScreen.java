package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EditorShopScreen extends AbstractContainerScreen<EditorShopContainer> {
    private static final int SLOT_SIZE = EditorShopContainer.SLOT_SIZE;
    private static final int SLOT_SPACING_Y = EditorShopContainer.SLOT_SPACING_Y;

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, Component.translatable("gui.fpsm.shop_editor.title"));
    }

    @Override
    protected void init() {
        this.imageWidth = menu.getImageWidth();
        this.imageHeight = menu.getImageHeight();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = Math.max(0, (this.height - this.imageHeight) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(leftPos + imageWidth / 2 - FPSMGuiTheme.BUTTON_LARGE_WIDTH / 2, topPos + imageHeight - 30,
                        FPSMGuiTheme.BUTTON_LARGE_WIDTH, FPSMGuiTheme.BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        this.renderBackground(guiGraphics);
        // 全屏背景
        guiGraphics.fill(0, 0, width, height, FPSMGuiTheme.BG_BASE);

        // 标题
        guiGraphics.drawCenteredString(font, title, leftPos + imageWidth / 2, topPos + 10, FPSMGuiTheme.TEXT_TITLE);
        guiGraphics.drawCenteredString(font,
                Component.literal(menu.getGameType() + " / " + menu.getMapName() + " / " + menu.getTeamName()),
                leftPos + imageWidth / 2, topPos + 24, FPSMGuiTheme.TEXT_SUB);

        // 分类标签 + 分隔线
        renderCategoryLabels(guiGraphics);

        // 槽位背景 + 价格叠加
        renderShopSlots(guiGraphics);
    }

    private void renderCategoryLabels(GuiGraphics guiGraphics) {
        Map<String, EditorShopContainer.TypeInfo> types = menu.getTypes();
        int rowIndex = 0;
        for (Map.Entry<String, EditorShopContainer.TypeInfo> entry : types.entrySet()) {
            String typeName = entry.getKey();
            int y = topPos + menu.getGridTop() + rowIndex * SLOT_SPACING_Y;

            // 分类标签
            Component label = Component.translatable("fpsm.shop.title." + typeName);
            int labelWidth = font.width(label);
            int labelX = leftPos + menu.getGridLeft() - 12 - labelWidth;
            guiGraphics.drawString(font, label, labelX, y + 4, FPSMGuiTheme.TEXT_SUB);

            // 分隔线（分类行下方）
            int lineY = y + SLOT_SPACING_Y - 4;
            guiGraphics.fill(leftPos + menu.getGridLeft() - 8, lineY, leftPos + menu.getGridLeft() + menu.getGridWidth(), lineY + 1,
                    FPSMGuiTheme.BORDER_INNER);

            rowIndex++;
        }
    }

    private void renderShopSlots(GuiGraphics guiGraphics) {
        List<ShopSlot> allSlots = menu.getAllSlots();
        int slotIndex = 0;
        int rowIndex = 0;

        for (Map.Entry<String, EditorShopContainer.TypeInfo> entry : menu.getTypes().entrySet()) {
            int slotCount = entry.getValue().slotCount();
            for (int col = 0; col < slotCount; col++) {
                if (slotIndex >= menu.slots.size()) break;
                Slot slot = menu.slots.get(slotIndex);
                int sx = this.leftPos + slot.x - 1;
                int sy = this.topPos + slot.y - 1;

                // 槽位背景
                boolean hovered = isSlotHovered(slot, mouseX, mouseY);
                int bgColor = hovered ? FPSMGuiTheme.BG_PANEL_HOVER : FPSMGuiTheme.BG_PANEL;
                int borderColor = hovered ? FPSMGuiTheme.ACCENT_PRIMARY : FPSMGuiTheme.BORDER_INNER;
                drawSlotBox(guiGraphics, sx, sy, bgColor, borderColor);

                // 价格叠加
                ShopSlot shopSlot = (slotIndex < allSlots.size()) ? allSlots.get(slotIndex) : null;
                if (shopSlot != null) {
                    String priceText = "$" + shopSlot.getDefaultCost();
                    guiGraphics.drawString(font, priceText, sx + 1, sy + SLOT_SIZE + 1, FPSMGuiTheme.TEXT_MUTED);
                }

                slotIndex++;
            }
            rowIndex++;
        }
    }

    private void drawSlotBox(GuiGraphics guiGraphics, int x, int y, int bg, int border) {
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bg);
        // 上边框
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + 1, border);
        // 下边框
        guiGraphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, border);
        // 左边框
        guiGraphics.fill(x, y + 1, x + 1, y + SLOT_SIZE - 1, border);
        // 右边框
        guiGraphics.fill(x + SLOT_SIZE - 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE - 1, border);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoveredSlotInfo(guiGraphics, mouseX, mouseY);
    }

    private int mouseX, mouseY;

    private boolean isSlotHovered(Slot slot, int mx, int my) {
        int sx = this.leftPos + slot.x - 1;
        int sy = this.topPos + slot.y - 1;
        return mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
    }

    private void renderHoveredSlotInfo(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredSlotIndex(mouseX, mouseY);
        if (hoveredIndex < 0) return;

        List<ShopSlot> allSlots = menu.getAllSlots();
        if (hoveredIndex >= allSlots.size()) return;
        ShopSlot slot = allSlots.get(hoveredIndex);
        if (slot == null) return;

        // 信息面板
        int panelX = mouseX + 12;
        int panelY = mouseY - 2;
        int panelW = 160;
        int panelH = 90;

        // 防止超出屏幕
        if (panelX + panelW > width) panelX = mouseX - panelW - 4;
        if (panelY + panelH > height) panelY = height - panelH - 4;
        if (panelY < 4) panelY = 4;

        // 面板背景
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0151820);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + 1, FPSMGuiTheme.ACCENT_PRIMARY);
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, FPSMGuiTheme.BG_PANEL);
        // 边框
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, FPSMGuiTheme.BORDER_INNER);
        guiGraphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, FPSMGuiTheme.BORDER_INNER);
        guiGraphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, FPSMGuiTheme.BORDER_INNER);

        int tx = panelX + 6;
        int ty = panelY + 6;
        int lineH = 11;

        // 物品名
        guiGraphics.drawString(font, slot.process().getDisplayName(), tx, ty, FPSMGuiTheme.TEXT_TITLE, false);
        ty += lineH + 2;

        // 价格
        guiGraphics.drawString(font,
                Component.translatable("gui.shop.slot.tooltip.default_cost").append("$" + slot.getDefaultCost()),
                tx, ty, FPSMGuiTheme.TEXT_BODY);
        ty += lineH;

        // 弹药
        guiGraphics.drawString(font,
                Component.translatable("gui.shop.slot.tooltip.ammo_count").append(String.valueOf(slot.getAmmoCount())),
                tx, ty, FPSMGuiTheme.TEXT_BODY);
        ty += lineH;

        // 分组
        guiGraphics.drawString(font,
                Component.translatable("gui.shop.slot.tooltip.group_id").append(String.valueOf(slot.getGroupId())),
                tx, ty, FPSMGuiTheme.TEXT_BODY);
        ty += lineH;

        // 监听器
        String listeners = slot.getListenerNames().toString();
        guiGraphics.drawString(font,
                Component.translatable("gui.shop.slot.tooltip.listeners").append(listeners),
                tx, ty, FPSMGuiTheme.TEXT_MUTED);
    }

    private int getHoveredSlotIndex(int mx, int my) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (isSlotHovered(menu.slots.get(i), mx, my)) return i;
        }
        return -1;
    }
}
