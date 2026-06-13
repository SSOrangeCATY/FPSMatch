package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EditorShopScreen extends FPSMWidgetContainerScreen<EditorShopContainer> {
    private static final int SLOT_SIZE = 18;

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, Component.translatable("gui.fpsm.shop_editor.title"));
    }

    @Override
    protected void buildUI() {
        // 多层背景
        root.addWidget(new WidgetGroup(2, 2, width - 2, height - 2)
                .setBackground(new ColorRectTexture(0x80000000)));
        root.addWidget(new WidgetGroup(0, 0, width, 1).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(0, height - 1, width, 1).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(0, 0, 1, height).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(width - 1, 0, 1, height).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(1, 1, width - 2, height - 2).setBackground(new ColorRectTexture(0xFF444444)));
        root.addWidget(new WidgetGroup(5, 5, width - 10, height - 10).setBackground(new ColorRectTexture(0xFF333333)));

        // 标题
        root.addWidget(new LabelWidget(width / 2 - font.width(title) / 2, 12, title.getString()).setTextColor(0xFFFFFFFF));
        Component sub = Component.literal(menu.getGameType() + " / " + menu.getMapName() + " / " + menu.getTeamName());
        root.addWidget(new LabelWidget(width / 2 - font.width(sub) / 2, 26, sub.getString()).setTextColor(0xFFB8D4E3));

        // 返回按钮
        root.addWidget(FPSMWidgets.button(width / 2 - 50, height - 30, 100, 20,
                Component.translatable("gui.back"), this::onClose));
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 背景由LDLib Widget组件渲染，此处留空
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderSlotOverlays(guiGraphics, mouseX, mouseY);
        renderShopSlotTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderSlotOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;

            // 插槽默认背景
            guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF333333);
            guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF777777);
            guiGraphics.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF777777);
            guiGraphics.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF777777);
            guiGraphics.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF777777);

            // 悬停高亮
            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                guiGraphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);
                guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFFFFFFFF);
                guiGraphics.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFFFFFFFF);
                guiGraphics.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFFFFFFFF);
                guiGraphics.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFFFFFFFF);
            }
        }
    }

    private void renderShopSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredCustomSlotIndex(mouseX, mouseY);
        if (hoveredIndex == -1) return;

        List<ShopSlot> allShopSlots = menu.getAllSlots();
        if (allShopSlots == null || hoveredIndex >= allShopSlots.size()) return;

        ShopSlot hoveredShopSlot = allShopSlots.get(hoveredIndex);
        if (hoveredShopSlot == null) return;

        List<Component> tooltipComponents = new ArrayList<>();
        tooltipComponents.add(Component.translatable("gui.shop.slot.tooltip.item")
                .append(hoveredShopSlot.process().getDisplayName()));
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

        guiGraphics.renderComponentTooltip(font, tooltipComponents, mouseX, mouseY);
    }

    private int getHoveredCustomSlotIndex(int mouseX, int mouseY) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;
            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }
}