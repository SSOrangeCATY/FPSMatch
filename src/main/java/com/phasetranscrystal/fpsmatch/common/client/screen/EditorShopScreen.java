package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EditorShopScreen extends AbstractContainerScreen<EditorShopContainer> {
    private static final int SLOT_SIZE = EditorShopContainer.SLOT_SIZE;
    private static final int GUI_SHADOW_COLOR = 0x80000000;
    private static final int GUI_MAIN_BACKGROUND = 0xFF444444;
    private static final int GUI_INNER_BORDER = 0xFF666666;
    private static final int GUI_OUTER_BORDER = 0xFF222222;
    private static final int SLOT_DEFAULT_BACKGROUND = 0xFF333333;
    private static final int SLOT_DEFAULT_BORDER = 0xFF777777;
    private static final int SLOT_HOVER_HIGHLIGHT = 0x80FFFFFF;
    private static final int SLOT_HOVER_BORDER = 0xFFFFFFFF;
    private static final int GUI_PADDING = 4;

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, Component.translatable("gui.fpsm.shop_editor.title"),
                Math.max(176, container.getImageWidth()),
                Math.max(166, container.getImageHeight()));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = Math.max(0, (this.height - this.imageHeight) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20)
                .build());
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor pGuiGraphicsExtractor, int pMouseX, int pMouseY) {
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderGuiMultiLayerBackground(guiGraphics);
        renderShopSlotsBackground(guiGraphics);
    }

    private int getSlotX(Slot slot) {
        return slot.x - 1;
    }

    private int getSlotY(Slot slot) {
        return slot.y - 1;
    }

    private void renderGuiMultiLayerBackground(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.fill(
                leftPos + 2, topPos + 2,
                leftPos + imageWidth + 2, topPos + imageHeight + 2,
                GUI_SHADOW_COLOR
        );

        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, GUI_OUTER_BORDER);
        guiGraphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, GUI_OUTER_BORDER);
        guiGraphics.fill(leftPos, topPos + 1, leftPos + 1, topPos + imageHeight - 1, GUI_OUTER_BORDER);
        guiGraphics.fill(leftPos + imageWidth - 1, topPos + 1, leftPos + imageWidth, topPos + imageHeight - 1, GUI_OUTER_BORDER);

        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, GUI_MAIN_BACKGROUND);

        int innerBorderX1 = leftPos + 1 + GUI_PADDING;
        int innerBorderY1 = topPos + 1 + GUI_PADDING;
        int innerBorderX2 = leftPos + imageWidth - 1 - GUI_PADDING;
        int innerBorderY2 = topPos + imageHeight - 1 - GUI_PADDING;

        guiGraphics.fill(innerBorderX1, innerBorderY1, innerBorderX2, innerBorderY1 + 1, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX1, innerBorderY2 - 1, innerBorderX2, innerBorderY2, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX1, innerBorderY1 + 1, innerBorderX1 + 1, innerBorderY2 - 1, GUI_INNER_BORDER);
        guiGraphics.fill(innerBorderX2 - 1, innerBorderY1 + 1, innerBorderX2, innerBorderY2 - 1, GUI_INNER_BORDER);
    }

    private void renderShopSlotsBackground(GuiGraphicsExtractor guiGraphics) {
        for (Slot shopSlot : menu.slots) {
            int slotRenderX = this.leftPos + getSlotX(shopSlot);
            int slotRenderY = this.topPos + getSlotY(shopSlot);

            guiGraphics.fill(slotRenderX, slotRenderY, slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE, SLOT_DEFAULT_BACKGROUND);
            guiGraphics.fill(slotRenderX, slotRenderY, slotRenderX + SLOT_SIZE, slotRenderY + 1, SLOT_DEFAULT_BORDER);
            guiGraphics.fill(slotRenderX, slotRenderY + SLOT_SIZE - 1, slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE, SLOT_DEFAULT_BORDER);
            guiGraphics.fill(slotRenderX, slotRenderY + 1, slotRenderX + 1, slotRenderY + SLOT_SIZE - 1, SLOT_DEFAULT_BORDER);
            guiGraphics.fill(slotRenderX + SLOT_SIZE - 1, slotRenderY + 1, slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE - 1, SLOT_DEFAULT_BORDER);
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        renderShopIdentity(guiGraphics);
        renderHoveredSlotHighlight(guiGraphics, mouseX, mouseY);
        renderShopSlotTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderShopIdentity(GuiGraphicsExtractor guiGraphics) {
        int centerX = leftPos + imageWidth / 2;
        guiGraphics.centeredText(font, title, centerX, topPos + 12, 0xFFFFFFFF);
        guiGraphics.centeredText(font,
                Component.literal(menu.getGameType() + " / " + menu.getMapName() + " / " + menu.getTeamName()),
                centerX, topPos + 26, 0xFFB8D4E3);
    }

    private void renderHoveredSlotHighlight(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredCustomSlotIndex(mouseX, mouseY);
        if (hoveredIndex == -1) {
            return;
        }

        Slot slot = menu.slots.get(hoveredIndex);
        int slotRenderX = this.leftPos + getSlotX(slot);
        int slotRenderY = this.topPos + getSlotY(slot);

        guiGraphics.fill(slotRenderX + 1, slotRenderY + 1, slotRenderX + SLOT_SIZE - 1, slotRenderY + SLOT_SIZE - 1, SLOT_HOVER_HIGHLIGHT);
        guiGraphics.fill(slotRenderX, slotRenderY, slotRenderX + SLOT_SIZE, slotRenderY + 1, SLOT_HOVER_BORDER);
        guiGraphics.fill(slotRenderX, slotRenderY + SLOT_SIZE - 1, slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE, SLOT_HOVER_BORDER);
        guiGraphics.fill(slotRenderX, slotRenderY + 1, slotRenderX + 1, slotRenderY + SLOT_SIZE - 1, SLOT_HOVER_BORDER);
        guiGraphics.fill(slotRenderX + SLOT_SIZE - 1, slotRenderY + 1, slotRenderX + SLOT_SIZE, slotRenderY + SLOT_SIZE - 1, SLOT_HOVER_BORDER);
    }

    private int getHoveredCustomSlotIndex(int mouseX, int mouseY) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int slotScreenX = this.leftPos + getSlotX(slot);
            int slotScreenY = this.topPos + getSlotY(slot);

            boolean isHoveredX = mouseX >= slotScreenX && mouseX < slotScreenX + SLOT_SIZE;
            boolean isHoveredY = mouseY >= slotScreenY && mouseY < slotScreenY + SLOT_SIZE;
            if (isHoveredX && isHoveredY) {
                return i;
            }
        }
        return -1;
    }

    private void renderShopSlotTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
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
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.listeners")
                .append(Component.literal(hoveredShopSlot.getListenerNames().toString())));
        tooltipComponents.add(Component.literal("\n"));
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.edit_prompt"));

        guiGraphics.setComponentTooltipForNextFrame(this.font, tooltipComponents, mouseX, mouseY);
    }
}
