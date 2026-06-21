package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.SaveSlotDataC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditShopSlotScreen extends AbstractContainerScreen<EditShopSlotMenu> {
    private final ContainerData data;
    private static final int SLOT_SIZE = 18;
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 200;

    private EditBox ammoField;
    private boolean isAmmoFieldAdded = false;
    private EditBox priceField;
    private EditBox groupField;

    public EditShopSlotScreen(EditShopSlotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.translatable("gui.fpsm.edit_shop_slot.title"));
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.data = new SimpleContainerData(3);
        int dataCount = Math.min(this.menu.getData().getCount(), 3);
        for (int i = 0; i < dataCount; i++) {
            data.set(i, this.menu.getData().get(i));
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.leftPos + this.imageWidth / 2;
        int startY = this.topPos + 38;

        // 物品槽位区域（居中上方）
        // 槽位已经在 container 中注册，这里不需要额外处理

        if (menu.isGun()) {
            createAmmoField(startY);
        }

        // 价格输入框
        int fieldY = startY + 56;
        this.priceField = new EditBox(this.font, centerX - 60, fieldY, 50, 16,
                Component.translatable("gui.fpsm.price"));
        this.priceField.setValue(String.valueOf(menu.getPrice()));
        this.priceField.setFilter(s -> s.matches("\\d*"));
        this.priceField.setResponder(s -> this.data.set(1, s.isEmpty() ? 0 : Integer.parseInt(s)));
        this.priceField.setTextColor(0xFFE2E8F0);
        this.priceField.setTextColorUneditable(0xFF8F9AA3);
        this.priceField.setBordered(true);
        this.priceField.setEditable(true);
        this.addRenderableWidget(this.priceField);

        // 组ID输入框
        this.groupField = new EditBox(this.font, centerX + 20, fieldY, 50, 16,
                Component.translatable("gui.fpsm.group"));
        this.groupField.setValue(String.valueOf(menu.getGroupId()));
        this.groupField.setFilter(s -> s.matches("-?\\d*"));
        this.groupField.setResponder(s -> data.set(2, s.isEmpty() ? -1 : Integer.parseInt(s)));
        this.groupField.setTextColor(0xFFE2E8F0);
        this.groupField.setTextColorUneditable(0xFF8F9AA3);
        this.groupField.setBordered(true);
        this.groupField.setEditable(true);
        this.addRenderableWidget(this.groupField);

        // 保存按钮
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.fpsm.shop_editor.save_button"), button -> onSaveButtonClick())
                .bounds(centerX - 55, this.topPos + PANEL_HEIGHT - 28, 110, 20)
                .build());
    }

    private void createAmmoField(int startY) {
        int centerX = this.leftPos + this.imageWidth / 2;
        this.ammoField = new EditBox(this.font, centerX - 60, startY + 38, 50, 16,
                Component.translatable("gui.fpsm.dummy_ammo"));
        this.ammoField.setValue(String.valueOf(menu.getAmmo()));
        this.ammoField.setFilter(s -> s.matches("\\d*"));
        this.ammoField.setResponder(s -> data.set(0, s.isEmpty() ? 0 : Integer.parseInt(s)));
        this.ammoField.setTextColor(0xFFE2E8F0);
        this.ammoField.setTextColorUneditable(0xFF8F9AA3);
        this.ammoField.setBordered(true);
        this.ammoField.setEditable(true);
        this.addRenderableWidget(this.ammoField);
        this.isAmmoFieldAdded = true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            openShopEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSaveButtonClick() {
        FPSMatch.INSTANCE.sendToServer(new SaveSlotDataC2SPacket(this.data));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            openShopEditor();
        }
    }

    private void openShopEditor() {
        FPSMatch.INSTANCE.sendToServer(new OpenShopEditorC2SPacket(menu.getGameType(), menu.getMapName(), menu.getTeamName()));
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        this.renderBackground(guiGraphics);

        int panelX = this.leftPos;
        int panelY = this.topPos;

        // 面板背景
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, FPSMGuiTheme.BG_BASE);
        // 外边框
        drawBorder(guiGraphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, FPSMGuiTheme.BORDER_OUTER);
        // 内边框
        drawBorder(guiGraphics, panelX + 2, panelY + 2, PANEL_WIDTH - 4, PANEL_HEIGHT - 4, FPSMGuiTheme.BORDER_INNER);

        // 标题区域分隔线
        guiGraphics.fill(panelX + 4, panelY + 32, panelX + PANEL_WIDTH - 4, panelY + 33, FPSMGuiTheme.BORDER_INNER);

        // 槽位背景
        renderSlotBackground(guiGraphics);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void renderSlotBackground(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, FPSMGuiTheme.BG_PANEL);
            drawBorder(guiGraphics, sx, sy, SLOT_SIZE, SLOT_SIZE, FPSMGuiTheme.BORDER_INNER);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        // 标题
        guiGraphics.drawString(font, this.title, this.leftPos + 8, this.topPos + 10, FPSMGuiTheme.TEXT_TITLE, false);

        // 物品标签
        guiGraphics.drawString(font, Component.translatable("gui.fpsm.shop_editor.item_label"),
                this.leftPos + 8, this.topPos + 22, FPSMGuiTheme.TEXT_MUTED, false);

        // 输入框标签
        if (menu.isGun() && ammoField != null) {
            guiGraphics.drawString(font, Component.translatable("gui.fpsm.dummy_ammo"),
                    ammoField.getX(), ammoField.getY() - 11, FPSMGuiTheme.TEXT_SUB, false);
        }
        if (priceField != null) {
            guiGraphics.drawString(font, Component.translatable("gui.fpsm.price"),
                    priceField.getX(), priceField.getY() - 11, FPSMGuiTheme.TEXT_SUB, false);
        }
        if (groupField != null) {
            guiGraphics.drawString(font, Component.translatable("gui.fpsm.group"),
                    groupField.getX(), groupField.getY() - 11, FPSMGuiTheme.TEXT_SUB, false);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 动态管理弹药输入框
        if (!menu.isGun()) {
            if (this.isAmmoFieldAdded && this.ammoField != null) {
                this.removeWidget(this.ammoField);
                this.isAmmoFieldAdded = false;
            }
        } else {
            if (this.ammoField == null) {
                int startY = this.topPos + 38;
                createAmmoField(startY);
            } else if (!this.isAmmoFieldAdded) {
                this.addRenderableWidget(this.ammoField);
                this.isAmmoFieldAdded = true;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoveredSlotHighlight(guiGraphics, mouseX, mouseY);
        renderCustomTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderHoveredSlotHighlight(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                guiGraphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x40FFFFFF);
                drawBorder(guiGraphics, sx, sy, SLOT_SIZE, SLOT_SIZE, FPSMGuiTheme.ACCENT_PRIMARY);
                break;
            }
        }
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics pGuiGraphics, int pX, int pY) {
        // 使用自定义 tooltip
    }

    private void renderCustomTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.menu.getCarried().isEmpty()) return;
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;

        ItemStack itemstack = this.hoveredSlot.getItem();
        List<Component> components = this.getTooltipFromContainerItem(itemstack);

        // 槽位0显示监听器信息
        if (hoveredSlot.index == 0) {
            List<String> lms = menu.getListeners();
            if (!lms.isEmpty()) {
                components.add(Component.literal(""));
                components.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
                components.add(Component.translatable("gui.fpsm.listener").append(": ").withStyle(ChatFormatting.DARK_AQUA));
                for (String lm : lms) {
                    components.add(Component.literal("  " + lm).withStyle(ChatFormatting.GRAY));
                }
                components.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
            }
        }
        guiGraphics.renderComponentTooltip(this.font, components, mouseX, mouseY);
    }
}
