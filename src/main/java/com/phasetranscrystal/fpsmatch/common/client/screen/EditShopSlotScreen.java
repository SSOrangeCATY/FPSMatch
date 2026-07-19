package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.ModularMenuUiSupport;
import com.phasetranscrystal.fpsmatch.common.client.screen.shop.ldlib2.Ldlib2EditShopSlotUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Thin Forge menu adapter.
 * LDLib2 owns custom panels and slot visuals; AbstractContainerScreen keeps the standard
 * render/mouse chain for inventory, carried stack and menu sync.
 */
public final class EditShopSlotScreen extends AbstractContainerScreen<EditShopSlotMenu> {
    private ModularUI modularUI;

    public EditShopSlotScreen(EditShopSlotMenu menu, Inventory inv, Component title) {
        super(menu, inv, Component.translatable("gui.fpsm.edit_shop_slot.title"));
        imageWidth = 360;
        imageHeight = 250;
    }

    @Override
    protected void init() {
        modularUI = Ldlib2EditShopSlotUi.create(menu, this::onClose);
        modularUI.setScreenAndInit(this);
        ModularMenuUiSupport.attach(modularUI, menu);
        imageWidth = Math.max(1, Math.round(modularUI.getWidth()));
        imageHeight = Math.max(1, Math.round(modularUI.getHeight()));
        super.init();
        addRenderableWidget(modularUI.getWidget());
        setFocused(modularUI.getWidget());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (modularUI != null) {
            ModularMenuUiSupport.syncSlotPositions(menu);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (modularUI != null) {
            modularUI.tick();
        }
    }

    @Override
    public void removed() {
        if (modularUI != null) {
            modularUI.onRemoved();
            modularUI = null;
        }
        super.removed();
    }
}
