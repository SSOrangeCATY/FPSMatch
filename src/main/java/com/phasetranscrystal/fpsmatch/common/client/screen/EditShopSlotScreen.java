package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.SaveSlotDataC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditShopSlotScreen extends FPSMWidgetContainerScreen<EditShopSlotMenu> {
    private static final int SLOT_SIZE = 18;
    private static final int FULL_BG_HEIGHT = 220;

    private final ContainerData data;
    private TextFieldWidget ammoField;
    private TextFieldWidget priceField;
    private TextFieldWidget groupField;

    public EditShopSlotScreen(EditShopSlotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200;
        this.imageHeight = 160;
        this.data = new SimpleContainerData(3);
        for (int i = 0; i < Math.min(menu.getData().getCount(), 3); i++) {
            data.set(i, menu.getData().get(i));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.topPos -= 20;
    }

    @Override
    protected void buildUI() {
        int guiX = leftPos - 20;
        int guiWidth = imageWidth + 20;

        // 多层背景
        root.addWidget(new WidgetGroup(guiX + 2, topPos + 2, guiWidth, FULL_BG_HEIGHT)
                .setBackground(new ColorRectTexture(0x80000000)));
        root.addWidget(new WidgetGroup(guiX, topPos, guiWidth, 1).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(guiX, topPos + FULL_BG_HEIGHT - 1, guiWidth, 1).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(guiX, topPos, 1, FULL_BG_HEIGHT).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(guiX + guiWidth - 1, topPos, 1, FULL_BG_HEIGHT).setBackground(new ColorRectTexture(0xFF222222)));
        root.addWidget(new WidgetGroup(guiX + 1, topPos + 1, guiWidth - 2, FULL_BG_HEIGHT - 2).setBackground(new ColorRectTexture(0xFF444444)));
        root.addWidget(new WidgetGroup(guiX + 5, topPos + 5, guiWidth - 10, FULL_BG_HEIGHT - 10).setBackground(new ColorRectTexture(0xFF333333)));

        // 标题
        root.addWidget(new LabelWidget(leftPos + titleLabelX - 20, topPos + titleLabelY - 15,
                title.getString()).setTextColor(0xFFFFFFFF));

        int centerX = leftPos + imageWidth / 2;
        int startY = topPos + 10;

        // 弹药字段
        if (menu.isGun()) {
            int slotX = leftPos + menu.getSlot(0).x - 10;
            int slotY = topPos + menu.getSlot(0).y + 30;
            ammoField = new TextFieldWidget(slotX, slotY, 40, 10,
                    () -> String.valueOf(data.get(0)),
                    s -> data.set(0, s.isEmpty() ? 0 : Integer.parseInt(s)));
            ammoField.setCurrentString(String.valueOf(menu.getAmmo()));
            ammoField.setValidator(s -> s.matches("\\d*") ? s : s.replaceAll("[^\\d]", ""));
            root.addWidget(ammoField);
            root.addWidget(new LabelWidget(ammoField.getSelfPosition().x, ammoField.getSelfPosition().y - 10,
                    Component.translatable("gui.fpsm.dummyAmmo").getString()).setTextColor(0xFFFFFFFF));
        }

        // 价格字段
        priceField = new TextFieldWidget(centerX, startY + 30, 40, 10,
                () -> String.valueOf(data.get(1)),
                s -> data.set(1, s.isEmpty() ? 0 : Integer.parseInt(s)));
        priceField.setCurrentString(String.valueOf(menu.getPrice()));
        priceField.setValidator(s -> s.matches("\\d*") ? s : s.replaceAll("[^\\d]", ""));
        root.addWidget(priceField);
        root.addWidget(new LabelWidget(priceField.getSelfPosition().x, priceField.getSelfPosition().y - 10,
                Component.translatable("gui.fpsm.price").getString()).setTextColor(0xFFFFFFFF));

        // 组字段
        groupField = new TextFieldWidget(centerX + 50, startY + 30, 40, 10,
                () -> String.valueOf(data.get(2)),
                s -> data.set(2, s.isEmpty() ? -1 : Integer.parseInt(s)));
        groupField.setCurrentString(String.valueOf(menu.getGroupId()));
        groupField.setValidator(s -> s.matches("-?\\d*") ? s : s.replaceAll("[^\\d-]", ""));
        root.addWidget(groupField);
        root.addWidget(new LabelWidget(groupField.getSelfPosition().x, groupField.getSelfPosition().y - 10,
                Component.translatable("gui.fpsm.group").getString()).setTextColor(0xFFFFFFFF));

        // 保存按钮
        root.addWidget(FPSMWidgets.button(leftPos + titleLabelX, topPos + imageHeight - 94 + 25, 100, 18,
                Component.translatable("gui.fpsm.shop_editor.save_button"), this::onSaveButtonClick));

        // 物品栏标签
        root.addWidget(new LabelWidget(8, imageHeight - 47, playerInventoryTitle.getString()).setTextColor(0xFFFFFFFF));
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
        FPSMatch.INSTANCE.sendToServer(new SaveSlotDataC2SPacket(data));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) openShopEditor();
    }

    private void openShopEditor() {
        FPSMatch.INSTANCE.sendToServer(new OpenShopEditorC2SPacket(menu.getGameType(), menu.getMapName(), menu.getTeamName()));
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 背景由LDLib Widget组件渲染，此处留空
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderSlotOverlays(guiGraphics, mouseX, mouseY);
    }

    private void renderSlotOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;

            guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF333333);
            guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF777777);
            guiGraphics.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF777777);
            guiGraphics.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF777777);
            guiGraphics.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF777777);

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                guiGraphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);
                guiGraphics.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFFFFFFFF);
                guiGraphics.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFFFFFFFF);
                guiGraphics.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFFFFFFFF);
                guiGraphics.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFFFFFFFF);
            }
        }
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics guiGraphics, int pX, int pY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack itemstack = hoveredSlot.getItem();
            List<Component> components = getTooltipFromContainerItem(itemstack);
            if (hoveredSlot.index == 0) {
                List<String> lms = menu.getListeners();
                if (!lms.isEmpty()) {
                    components.add(Component.literal("\n"));
                    components.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
                    components.add(Component.translatable("gui.fpsm.listener").append(": ").withStyle(ChatFormatting.DARK_AQUA));
                    for (String lm : lms) {
                        components.add(Component.literal("- " + lm).withStyle(ChatFormatting.GRAY));
                    }
                    components.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
                }
            }
            guiGraphics.renderTooltip(font, components, itemstack.getTooltipImage(), itemstack, pX, pY);
        }
    }
}