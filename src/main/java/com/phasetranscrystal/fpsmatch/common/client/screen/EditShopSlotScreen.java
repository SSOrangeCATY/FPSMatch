package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenEditorC2SPacket;
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
    private static final int GUI_SHADOW_COLOR = 0x80000000;
    private static final int GUI_MAIN_BACKGROUND = 0xFF444444;
    private static final int GUI_INNER_BORDER = 0xFF666666;
    private static final int GUI_OUTER_BORDER = 0xFF222222;
    private static final int SLOT_DEFAULT_BACKGROUND = 0xFF333333;
    private static final int SLOT_DEFAULT_BORDER = 0xFF777777;
    private static final int SLOT_HOVER_HIGHLIGHT = 0x80FFFFFF;
    private static final int SLOT_HOVER_BORDER = 0xFFFFFFFF;
    private static final int GUI_PADDING = 4;
    private static final int FULL_BACKGROUND_HEIGHT = 220;

    private EditBox ammoFiled;
    private boolean isAmmoFiledAdded = false;

    private EditBox priceField;
    private EditBox groupField;

    public EditShopSlotScreen(EditShopSlotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200;
        this.imageHeight = 160;
        this.data = new SimpleContainerData(3);
        int dataCount = Math.min(this.menu.getData().getCount(), 3);
        for (int i = 0; i < dataCount; i++) {
            data.set(i, this.menu.getData().get(i));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.topPos -= 20;
        int centerX = this.leftPos + (this.imageWidth / 2);
        int startY = this.topPos + 10;
        int slotX = this.leftPos + this.menu.getSlot(0).x - 10;
        int slotY = this.topPos + this.menu.getSlot(0).y + 30;

        if(menu.isGun()){
            this.ammoFiled = new EditBox(this.font, slotX, slotY, 40, 10, Component.translatable("gui.fpsm.dummy_ammo"));
            this.ammoFiled.setValue(String.valueOf(menu.getAmmo()));
            this.ammoFiled.setFilter(s -> s.matches("\\d*"));
            this.ammoFiled.setResponder(
                    s -> data.set(0, s.isEmpty() ? 0 : Integer.parseInt(s))
            );
            this.addRenderableWidget(this.ammoFiled);
            this.isAmmoFiledAdded = true;
        }

        this.priceField = new EditBox(this.font, centerX, startY + 30, 40, 10, Component.translatable("gui.fpsm.price"));
        this.priceField.setValue(String.valueOf(menu.getPrice()));
        this.priceField.setFilter(s -> s.matches("\\d*"));
        this.priceField.setResponder(
                s -> this.data.set(1, s.isEmpty() ? 0 : Integer.parseInt(s))
        );
        this.addRenderableWidget(this.priceField);

        this.groupField = new EditBox(this.font, centerX + 50, startY + 30, 40, 10, Component.translatable("gui.fpsm.group"));
        this.groupField.setValue(String.valueOf(menu.getGroupId()));
        this.groupField.setFilter(s -> s.matches("\\d*"));
        this.groupField.setResponder(
                s -> data.set(2, s.isEmpty() ? -1 : Integer.parseInt(s))
        );
        this.addRenderableWidget(this.groupField);

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.shop_editor.save_button"), button -> onSaveButtonClick())
                .pos(this.leftPos + this.titleLabelX, this.topPos + this.imageHeight - 94 + 25)
                .size(100, 18)
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            FPSMatch.INSTANCE.sendToServer(new OpenEditorC2SPacket());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSaveButtonClick() {
        FPSMatch.INSTANCE.sendToServer(new SaveSlotDataC2SPacket(this.data));

        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null) {
            FPSMatch.INSTANCE.sendToServer(new OpenEditorC2SPacket());
        }
    }

    private void drawLabel(GuiGraphics guiGraphics, Component text, EditBox field) {
        guiGraphics.drawString(this.font, text, field.getX() - this.leftPos, field.getY() - this.topPos - 10, 0xFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        pGuiGraphics.drawString(this.font, this.title, this.titleLabelX - 20, this.titleLabelY - 15, 0xFFFFFF, false);
        if(menu.isGun()){
            drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.dummyAmmo"), ammoFiled);
        }
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.price"), priceField);
        drawLabel(pGuiGraphics, Component.translatable("gui.fpsm.group"), groupField);
        int inventoryLabelY = this.imageHeight - 47;
        pGuiGraphics.drawString(this.font, this.playerInventoryTitle, 8, inventoryLabelY, 0xFFFFFF, false);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderGuiMultiLayerBackground(guiGraphics);
        renderShopSlotsBackground(guiGraphics);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if(!menu.isGun()){
            if(this.isAmmoFiledAdded){
                this.removeWidget(this.ammoFiled);
                this.isAmmoFiledAdded = false;
            }
        }else {
            if (!this.isAmmoFiledAdded && this.ammoFiled != null) {
                this.addRenderableWidget(this.ammoFiled);
                this.isAmmoFiledAdded = true;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderHoveredSlotHighlight(guiGraphics, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics pGuiGraphics, int pX, int pY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack itemstack = this.hoveredSlot.getItem();
            List<Component> components = this.getTooltipFromContainerItem(itemstack);
            if(hoveredSlot.index == 0){
                List<String> lms = menu.getListeners();
                if(!lms.isEmpty()) {
                    components.add(Component.literal("\n"));
                    Component line = Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD);
                    components.add(line);
                    Component lmt = Component.translatable("gui.fpsm.listener").append(": ").withStyle(ChatFormatting.DARK_AQUA);
                    components.add(lmt);
                    for (String lm : menu.getListeners()) {
                        components.add(Component.literal("- "+lm).withStyle(ChatFormatting.GRAY));
                    }
                    components.add(line);
                }
            }
            pGuiGraphics.renderTooltip(this.font, components, itemstack.getTooltipImage(), itemstack, pX, pY);
        }

    }

    private int getSlotX(Slot slot){
        return slot.x - 1;
    }

    private int getSlotY(Slot slot){
        return slot.y - 1;
    }

    private void renderGuiMultiLayerBackground(GuiGraphics guiGraphics) {
        int guiX = this.leftPos - 20;
        int guiY = this.topPos;
        int guiWidth = this.imageWidth + 20;
        int fullGuiHeight = FULL_BACKGROUND_HEIGHT;

        guiGraphics.fill(
                guiX + 2, guiY + 2,
                guiX + guiWidth + 2, guiY + fullGuiHeight + 2,
                GUI_SHADOW_COLOR
        );

        guiGraphics.fill(
                guiX, guiY,
                guiX + guiWidth, guiY + 1,
                GUI_OUTER_BORDER
        );
        guiGraphics.fill(
                guiX, guiY + fullGuiHeight - 1,
                guiX + guiWidth, guiY + fullGuiHeight,
                GUI_OUTER_BORDER
        );
        guiGraphics.fill(
                guiX, guiY + 1,
                guiX + 1, guiY + fullGuiHeight - 1,
                GUI_OUTER_BORDER
        );
        guiGraphics.fill(
                guiX + guiWidth - 1, guiY + 1,
                guiX + guiWidth, guiY + fullGuiHeight - 1,
                GUI_OUTER_BORDER
        );

        guiGraphics.fill(
                guiX + 1, guiY + 1,
                guiX + guiWidth - 1, guiY + fullGuiHeight - 1,
                GUI_MAIN_BACKGROUND
        );

        int innerBorderX1 = guiX + 1 + GUI_PADDING;
        int innerBorderY1 = guiY + 1 + GUI_PADDING;
        int innerBorderX2 = guiX + guiWidth - 1 - GUI_PADDING;
        int innerBorderY2 = guiY + fullGuiHeight - 1 - GUI_PADDING;

        guiGraphics.fill(innerBorderX1, innerBorderY1, innerBorderX2, innerBorderY1 + 1, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX1, innerBorderY2 - 1, innerBorderX2, innerBorderY2, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX1, innerBorderY1 + 1, innerBorderX1 + 1, innerBorderY2 - 1, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX2 - 1, innerBorderY1 + 1, innerBorderX2, innerBorderY2 - 1, GUI_INNER_BORDER);
    }

    private void renderShopSlotsBackground(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int slotRenderX = this.leftPos + getSlotX(slot);
            int slotRenderY = this.topPos + getSlotY(slot);

            guiGraphics.fill(
                    slotRenderX, slotRenderY,
                    slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE,
                    SLOT_DEFAULT_BACKGROUND
            );

            guiGraphics.fill(
                    slotRenderX, slotRenderY,
                    slotRenderX + SLOT_SIZE, slotRenderY + 1,
                    SLOT_DEFAULT_BORDER
            );
            guiGraphics.fill(
                    slotRenderX, slotRenderY + SLOT_SIZE - 1,
                    slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE,
                    SLOT_DEFAULT_BORDER
            );
            guiGraphics.fill(
                    slotRenderX, slotRenderY + 1,
                    slotRenderX + 1, slotRenderY + SLOT_SIZE - 1,
                    SLOT_DEFAULT_BORDER
            );
            guiGraphics.fill(
                    slotRenderX + SLOT_SIZE - 1, slotRenderY + 1,
                    slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE - 1,
                    SLOT_DEFAULT_BORDER
            );
        }
    }

    private int getHoveredCustomSlotIndex(int mouseX, int mouseY) {
        int relativeMouseX = mouseX - this.leftPos;
        int relativeMouseY = mouseY - this.topPos;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int slotRelX = getSlotX(slot);
            int slotRelY = getSlotY(slot);

            boolean isHoveredX = relativeMouseX >= slotRelX && relativeMouseX < slotRelX + SLOT_SIZE;
            boolean isHoveredY = relativeMouseY >= slotRelY && relativeMouseY < slotRelY + SLOT_SIZE;
            if (isHoveredX && isHoveredY) {
                return i;
            }
        }
        return -1;
    }

    private void renderHoveredSlotHighlight(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredCustomSlotIndex(mouseX, mouseY);
        if (hoveredIndex == -1) {
            return;
        }

        Slot slot = menu.slots.get(hoveredIndex);
        int slotRenderX = this.leftPos + getSlotX(slot);
        int slotRenderY = this.topPos + getSlotY(slot);

        guiGraphics.fill(
                slotRenderX + 1, slotRenderY + 1,
                slotRenderX + SLOT_SIZE - 1, slotRenderY + SLOT_SIZE - 1,
                SLOT_HOVER_HIGHLIGHT
        );

        guiGraphics.fill(
                slotRenderX, slotRenderY,
                slotRenderX + SLOT_SIZE, slotRenderY + 1,
                SLOT_HOVER_BORDER
        );
        guiGraphics.fill(
                slotRenderX, slotRenderY + SLOT_SIZE - 1,
                slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE,
                SLOT_HOVER_BORDER
        );
        guiGraphics.fill(
                slotRenderX, slotRenderY + 1,
                slotRenderX + 1, slotRenderY + SLOT_SIZE - 1,
                SLOT_HOVER_BORDER
        );
        guiGraphics.fill(
                slotRenderX + SLOT_SIZE - 1, slotRenderY + 1,
                slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE - 1,
                SLOT_HOVER_BORDER
        );
    }
}