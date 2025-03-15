package com.phasetranscrystal.fpsmatch.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;


public class EditShopSlotScreen extends AbstractContainerScreen<EditShopSlotMenu> {
    //    private final ResourceLocation TEXTURE = new ResourceLocation("fpsm", "textures/gui/edit_shop_slot_screen.png");
    private EditBox ammoFiled;
    private EditBox nameField;
    private EditBox priceField;
    private EditBox groupField;
    private EditBox listenerField;

    public EditShopSlotScreen(EditShopSlotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200; // GUI 宽度
        this.imageHeight = 160; // GUI 高度
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.leftPos + (this.imageWidth / 2);
        int startY = this.topPos + 10;
        int slotX = this.leftPos + this.menu.getSlot(0).x;
        int slotY = this.topPos + this.menu.getSlot(0).y + 20;
        //虚拟弹药输入框
        this.ammoFiled = new EditBox(this.font, slotX, slotY, 40, 10, Component.translatable("gui.fpsm.dummy_ammo"));
        this.addRenderableWidget(this.ammoFiled);

        // 名字文本框
        this.nameField = new EditBox(this.font, centerX, startY, 80, 10, Component.translatable("gui.fpsm.name"));
        this.nameField.setValue("");
        this.addRenderableWidget(this.nameField);

        // 价格输入框
        this.priceField = new EditBox(this.font, centerX, startY + 30, 40, 10, Component.translatable("gui.fpsm.price"));
        this.priceField.setValue(String.valueOf(menu.getPrice()));
        this.addRenderableWidget(this.priceField);

        // 分组 ID 文本框
        this.groupField = new EditBox(this.font, centerX + 50, startY + 30, 40, 10, Component.translatable("gui.fpsm.group"));
        this.groupField.setValue(String.valueOf(menu.getGroupId()));
        this.addRenderableWidget(this.groupField);

        // 监听模块文本框
        this.listenerField = new EditBox(this.font, centerX, startY + 60, 80, 10, Component.translatable("gui.fpsm.listener"));
        this.listenerField.setValue(String.valueOf(menu.getListenerId()));
        this.addRenderableWidget(this.listenerField);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        //虚拟弹药
        Slot slot = this.menu.getSlot(0);
//        pGuiGraphics.drawString(this.font, "$"+ slotCost.get(0), x, y, 0xFFFFFF);
        //物品栏标签
        int inventoryLabelY = this.imageHeight - 94 + 25; // +25下移
        pGuiGraphics.drawString(this.font, this.playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
//        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = this.leftPos;
        int y = this.topPos;
//        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
