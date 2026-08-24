package com.ptcrys.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.ModularMenuUiSupport;
import com.ptcrys.fpsmatch.common.client.screen.shop.ldlib2.Ldlib2ShopEditorUi;
import com.ptcrys.fpsmatch.common.packet.shop.ShopConfigToolActionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Thin Forge menu adapter.
 * LDLib2 owns custom panels and slot visuals; AbstractContainerScreen keeps the standard
 * render/mouse chain for carried stack and menu sync.
 */
public final class EditorShopScreen extends AbstractContainerScreen<EditorShopContainer> {
    private ModularUI modularUI;

    public EditorShopScreen(EditorShopContainer container, Inventory inv, Component title) {
        super(container, inv, Component.translatable("gui.fpsm.shop_editor.title"));
        imageWidth = 520;
        imageHeight = Math.max(260, container.getImageHeight());
    }

    @Override
    protected void init() {
        modularUI = Ldlib2ShopEditorUi.create(menu, this::onClose);
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

    @Override
    public void onClose() {
        // 关闭编辑器时返回商店配置列表，使玩家可以继续选择/编辑其它商店条目（否则会被困在当前商店内，必须重进才可再编辑）
        minecraft.player.closeContainer();
        FPSMatch.sendToServer(new ShopConfigToolActionC2SPacket(
                ShopConfigToolActionC2SPacket.Action.REFRESH,
                menu.getGameType(),
                menu.getMapName()
        ));
        minecraft.setScreen(null);
    }
}
