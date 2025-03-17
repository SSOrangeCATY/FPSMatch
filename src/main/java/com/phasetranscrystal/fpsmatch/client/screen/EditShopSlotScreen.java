package com.phasetranscrystal.fpsmatch.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

import java.util.Map;


public class EditShopSlotScreen extends AbstractContainerScreen<EditShopSlotMenu> {
    private final ContainerData data;
    //    private final ResourceLocation TEXTURE = new ResourceLocation("fpsm", "textures/gui/edit_shop_slot_screen.png");
    private EditBox ammoFiled;
    private EditBox nameField;
    private EditBox priceField;
    private EditBox groupField;
    private EditBox listenerField;

    public EditShopSlotScreen(EditShopSlotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.data = this.menu.getData();
        this.imageWidth = 200; // GUI 宽度
        this.imageHeight = 160; // GUI 高度
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.leftPos + (this.imageWidth / 2);
        int startY = this.topPos + 10;
        int slotX = this.leftPos + this.menu.getSlot(0).x - 10;
        int slotY = this.topPos + this.menu.getSlot(0).y + 30;
        //虚拟弹药输入框
        this.ammoFiled = new EditBox(this.font, slotX, slotY, 40, 10, Component.translatable("gui.fpsm.dummy_ammo"));
        this.ammoFiled.setValue(String.valueOf(menu.getAmmo()));
        this.ammoFiled.setFilter(s -> s.matches("\\d+"));//只能输入0-9组成的数字，不能为空
        //监听,所有输入框同理
        this.ammoFiled.setResponder(
                s -> data.set(0, Integer.parseInt(s))
        );
        this.addRenderableWidget(this.ammoFiled);

        // 名字 文本框
        this.nameField = new EditBox(this.font, centerX, startY, 80, 10, Component.translatable("gui.fpsm.name"));
        this.nameField.setValue(menu.getName());
        this.nameField.setEditable(false);
        this.addRenderableWidget(this.nameField);

        // 价格输入框
        this.priceField = new EditBox(this.font, centerX, startY + 30, 40, 10, Component.translatable("gui.fpsm.price"));
        this.priceField.setValue(String.valueOf(menu.getPrice()));
        this.priceField.setFilter(s -> s.matches("\\d+"));//只能输入0-9组成的数字，不能为空
        this.priceField.setResponder(
                s -> {
                    data.set(1, Integer.parseInt(s));
                    System.out.println("存在！" + this.menu.getPrice());
                }
        );
        this.addRenderableWidget(this.priceField);

        // 分组 ID 输入框
        this.groupField = new EditBox(this.font, centerX + 50, startY + 30, 40, 10, Component.translatable("gui.fpsm.group"));
        this.groupField.setValue(String.valueOf(menu.getGroupId()));
        this.groupField.setFilter(s -> s.matches("\\d+"));//只能输入0-9组成的数字，不能为空
        this.groupField.setResponder(
                s -> data.set(2, Integer.parseInt(s))
        );
        this.addRenderableWidget(this.groupField);

        // 监听模块 文本框
        this.listenerField = new EditBox(this.font, centerX, startY + 60, 80, 10, Component.translatable("gui.fpsm.listener"));
        this.listenerField.setValue(String.valueOf(menu.getListeners()));
        this.listenerField.setEditable(false);
        this.addRenderableWidget(this.listenerField);

        //保存按钮
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.shop_editor.save_button"), button -> {
            onSaveButtonClick();
        })
                .pos(this.titleLabelX, this.imageHeight - 94 + 25)   // 设置按钮的位置
                .size(100, 18)                                      // 设置按钮的大小
                .createNarration((Button.CreateNarration) null)      // 设置无障碍功能
                .build());
    }

    // 处理保存按钮点击事件
    private void onSaveButtonClick() {

    }

    private void drawLabel(GuiGraphics guiGraphics, Component text, EditBox field, int color) {
        guiGraphics.drawString(this.font, text, field.getX() - this.leftPos, field.getY() - this.topPos - 10, color);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        //二级菜单标题
        pGuiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY - 15, 0x404040, false);
        //各个输入框标签：
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.dummy_ammo"), ammoFiled, 0xFFFFFF);
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.name"), nameField, 0xFFFFFF);
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.price"), priceField, 0xFFFFFF);
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.group"), groupField, 0xFFFFFF);
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.listener"), listenerField, 0xFFFFFF);
        //物品栏标签
        int inventoryLabelY = this.imageHeight - 94 + 45; // +45下移
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
