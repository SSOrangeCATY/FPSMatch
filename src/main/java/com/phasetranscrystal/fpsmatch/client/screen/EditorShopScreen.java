package com.phasetranscrystal.fpsmatch.client.screen;

import com.tacz.guns.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EditorShopScreen extends AbstractContainerScreen<EditorShopContainer> {
    private final List<EditBox> textBoxes = new ArrayList<>();
    private static final int SLOT_SIZE = 18; // 原版槽位尺寸
    private static final int TEXTBOX_HEIGHT = (int) (SLOT_SIZE * 0.618); // 黄金比例高度 ≈11px

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222; // 调整总高度以容纳文本框和按钮
        this.inventoryLabelY = this.imageHeight - 94; // 下移玩家物品栏标签
    }

    @Override
    protected void init() {
        super.init();

        // 创建 4x4 文本框（每个槽位下方）
        int startX = 8;
        int startY = 18 + SLOT_SIZE + 2; // 槽位下方间距
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                EditBox textBox = new EditBox(
                        this.font,
                        leftPos + startX + col * SLOT_SIZE,
                        topPos + startY + row * SLOT_SIZE,
                        SLOT_SIZE - 2, // 宽度略小于槽位
                        TEXTBOX_HEIGHT,
                        Component.literal("")
                );
                textBox.setMaxLength(3); // 限制输入3位数字
                textBox.setFilter(s -> s.matches("\\d*")); // 仅允许数字
                textBoxes.add(textBox);
                addRenderableWidget(textBox);
            }
        }

        // 添加确认按钮（位于底部中央）
        int buttonWidth = 50;
        int buttonX = leftPos + (imageWidth - buttonWidth) / 2;
        int buttonY = topPos + imageHeight - 30;
        addRenderableWidget(new Button(
                buttonX, buttonY, buttonWidth, 20,
                Component.literal("确认并保存"),
                button -> saveAndClose()
        ));
    }

    private void saveAndClose() {
        // 收集所有文本框的值并发送到服务端
        List<Integer> values = textBoxes.stream()
                .map(box -> box.getValue().isEmpty() ? 0 : Integer.parseInt(box.getValue()))
                .collect(Collectors.toList());
        NetworkHandler.CHANNEL.sendToServer(new NumberInputPacket(values));
        this.onClose(); // 关闭界面
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 绘制背景（基于原版箱子材质）
        guiGraphics.blit(
                new ResourceLocation("textures/gui/container/generic_54.png"),
                leftPos, topPos,
                0, 0,
                imageWidth, imageHeight
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY); // 绘制物品提示
    }
}