package com.phasetranscrystal.fpsmatch.client.screen;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;


public class EditorShopScreen extends AbstractContainerScreen<EditorShopContainer> {
    private List<Integer> slotCost = new ArrayList<>();
    private static final int SLOT_SIZE = 18;
    private static final int d = 10; // **Slot 之间的间隔**
    private static final int TEXTBOX_HEIGHT = Math.max(1, (int) (d * 0.618)); // **文本框高度**
    private static final int COLUMNS = 5;
    private static final int ROWS = 5;


    //左边距 = 右边距 = 8px（通常保持和原版 UI 统一）

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, Component.translatable("gui.fpsm.shop_editor.title"));
        this.imageWidth = 146;
        this.imageHeight = 220;//148自定义GUI部分+物品栏部分72
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
    }


    //渲染价格
    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        this.menu.getAllSlots().forEach(slot -> slotCost.add(slot.getCost()));
        for (int i = 0; i < ROWS * COLUMNS; i++) {
            Slot slot = this.menu.slots.get(i);
            int x = slot.x;
            int y = slot.y + SLOT_SIZE + TEXTBOX_HEIGHT - d / 2;
            pGuiGraphics.drawString(this.font, "$" + slotCost.get(i), x, y, 0xFFFFFF);
        }
        //物品栏标签
        pGuiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.menu.getSlot(EditorShopContainer.PLAYER_INV_START).x - 15, this.inventoryLabelY + 22, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        this.renderBackground(guiGraphics);  // **确保默认 GUI 背景被渲染**
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }


}
