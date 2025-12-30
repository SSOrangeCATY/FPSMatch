package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EditorShopScreen extends AbstractContainerScreen<EditorShopContainer> {
    private static final int SLOT_SIZE = 18;
    private final int guiX = 0;
    private final int guiY = 0;
    // GUI阴影色
    private static final int GUI_SHADOW_COLOR = 0x80000000;
    // GUI主背景色
    private static final int GUI_MAIN_BACKGROUND = 0xFF444444;
    // GUI内层边框色
    private static final int GUI_INNER_BORDER = 0xFF666666;
    // GUI外层边框色
    private static final int GUI_OUTER_BORDER = 0xFF222222;
    // 插槽默认背景色
    private static final int SLOT_DEFAULT_BACKGROUND = 0xFF333333;
    // 插槽默认边框色
    private static final int SLOT_DEFAULT_BORDER = 0xFF777777;
    // 插槽悬停高亮色
    private static final int SLOT_HOVER_HIGHLIGHT = 0x80FFFFFF;
    // 插槽悬停边框色
    private static final int SLOT_HOVER_BORDER = 0xFFFFFFFF;
    // GUI内边距
    private static final int GUI_PADDING = 4;

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, Component.translatable("gui.fpsm.shop_editor.title"));
    }

    @Override
    protected void init() {
        this.leftPos = guiX;
        this.topPos = guiY;
        calculateGuiPosition();
    }


    private void calculateGuiPosition() {
        Minecraft mc = Minecraft.getInstance();
        this.imageWidth = mc.getWindow().getWidth();
        this.imageHeight = mc.getWindow().getHeight();
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        this.renderBackground(guiGraphics);

        calculateGuiPosition();

        renderGuiMultiLayerBackground(guiGraphics);

        renderShopSlotsBackground(guiGraphics);
    }

    private int getSlotX(Slot slot){
        return slot.x - 1;
    }

    private int getSlotY(Slot slot){
        return slot.y - 1;
    }


    private void renderGuiMultiLayerBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(
                guiX + 2, guiY + 2,
                guiX + imageWidth + 2, guiY + imageHeight + 2,
                GUI_SHADOW_COLOR
        );

        guiGraphics.fill(
                guiX, guiY,
                guiX + imageWidth, guiY + 1,
                GUI_OUTER_BORDER
        );
        guiGraphics.fill(
                guiX, guiY + imageHeight - 1,
                guiX + imageWidth, guiY + imageHeight,
                GUI_OUTER_BORDER
        );
        guiGraphics.fill(
                guiX, guiY + 1,
                guiX + 1, guiY + imageHeight - 1,
                GUI_OUTER_BORDER
        );
        guiGraphics.fill(
                guiX + imageWidth - 1, guiY + 1,
                guiX + imageWidth, guiY + imageHeight - 1,
                GUI_OUTER_BORDER
        );

        guiGraphics.fill(
                guiX + 1, guiY + 1,
                guiX + imageWidth - 1, guiY + imageHeight - 1,
                GUI_MAIN_BACKGROUND
        );

        int innerBorderX1 = guiX + 1 + GUI_PADDING;
        int innerBorderY1 = guiY + 1 + GUI_PADDING;
        int innerBorderX2 = guiX + imageWidth - 1 - GUI_PADDING;
        int innerBorderY2 = guiY + imageHeight - 1 - GUI_PADDING;

        guiGraphics.fill(innerBorderX1, innerBorderY1, innerBorderX2, innerBorderY1 + 1, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX1, innerBorderY2 - 1, innerBorderX2, innerBorderY2, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX1, innerBorderY1 + 1, innerBorderX1 + 1, innerBorderY2 - 1, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX2 - 1, innerBorderY1 + 1, innerBorderX2, innerBorderY2 - 1, GUI_INNER_BORDER);
    }

    private void renderShopSlotsBackground(GuiGraphics guiGraphics) {
        for (Slot shopSlot : menu.slots) {
            int slotRenderX = this.leftPos + getSlotX(shopSlot);
            int slotRenderY = this.topPos + getSlotY(shopSlot);

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

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoveredSlotHighlight(guiGraphics, mouseX, mouseY);
        renderShopSlotTooltip(guiGraphics, mouseX, mouseY);
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

    private int getHoveredCustomSlotIndex(int mouseX, int mouseY) {
        int relativeMouseX = mouseX - this.leftPos;
        int relativeMouseY = mouseY - this.topPos;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int slotRelX = this.leftPos + getSlotX(slot);
            int slotRelY = this.topPos + getSlotY(slot);

            boolean isHoveredX = relativeMouseX >= slotRelX && relativeMouseX < slotRelX + SLOT_SIZE;
            boolean isHoveredY = relativeMouseY >= slotRelY && relativeMouseY < slotRelY + SLOT_SIZE;
            if (isHoveredX && isHoveredY) {
                return i;
            }
        }
        return -1;
    }

    private void renderShopSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredCustomSlotIndex(mouseX, mouseY);
        if (hoveredIndex == -1) {
            return;
        }

        List<ShopSlot> allShopSlots = menu.getAllSlots();
        if (allShopSlots == null || hoveredIndex >= allShopSlots.size()) {
            return;
        }

        ShopSlot hoveredShopSlot = allShopSlots.get(hoveredIndex);
        if (hoveredShopSlot == null) {
            return;
        }

        List<Component> tooltipComponents = new ArrayList<>();
        Component itemName = hoveredShopSlot.process().getDisplayName();

        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.item").append(itemName));
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.default_cost")
                .append(Component.literal("$" + hoveredShopSlot.getDefaultCost())));
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.ammo_count")
                .append(Component.literal(String.valueOf(hoveredShopSlot.getAmmoCount()))));
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.group_id")
                .append(Component.literal(String.valueOf(hoveredShopSlot.getGroupId()))));
        String listeners = hoveredShopSlot.getListenerNames().toString();
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.listeners")
                .append(Component.literal(listeners)));
        tooltipComponents.add(Component.literal("\n"));
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.edit_prompt"));

        guiGraphics.renderComponentTooltip(this.font, tooltipComponents, mouseX, mouseY);
    }
}