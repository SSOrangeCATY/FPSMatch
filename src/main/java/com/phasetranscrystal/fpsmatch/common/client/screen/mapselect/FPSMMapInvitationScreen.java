package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapInvitationScreen extends FPSMMapScreenBase {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 116;

    private final MapRoomInvitationS2CPacket invitation;
    private final Screen parent;

    public FPSMMapInvitationScreen(MapRoomInvitationS2CPacket invitation, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.invitation.title"));
        this.invitation = invitation;
        this.parent = parent;
    }

    public Screen parentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - PANEL_HEIGHT / 2;
        // 接受/拒绝按钮，统一大按钮 110，间距 8
        int total = 2;
        int bw = FPSMGuiTheme.BUTTON_LARGE_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;
        int[] xs = new int[total];
        for (int i = 0; i < total; i++) {
            xs[i] = buttonX(centerX, bw, gap, i, total);
        }
        addRenderableWidget(createLargeButton(Component.translatable("gui.fpsm.map_select.invitation.accept"), xs[0], top + 78,
                button -> accept()));
        addRenderableWidget(createLargeButton(Component.translatable("gui.fpsm.map_select.invitation.reject"), xs[1], top + 78,
                button -> reject()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - PANEL_WIDTH / 2;
        int top = height / 2 - PANEL_HEIGHT / 2;
        // 弹窗面板背景（统一）
        drawPanel(graphics, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, FPSMGuiTheme.BG_PANEL);
        graphics.drawCenteredString(font, title, width / 2, top + 14, FPSMGuiTheme.TEXT_TITLE);
        graphics.drawCenteredString(font, invitation.message(), width / 2, top + 42, FPSMGuiTheme.TEXT_HIGHLIGHT);
        graphics.drawCenteredString(font, Component.literal(invitation.gameType() + " / " + invitation.mapName()), width / 2, top + 58, FPSMGuiTheme.TEXT_SUB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        reject();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void accept() {
        FPSMClient.getGlobalData().clearMapRoomInvitation();
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.ACCEPT_INVITE, invitation.gameType(), invitation.mapName(), new UUID(0L, 0L)));
        minecraft.setScreen(parent);
    }

    private void reject() {
        FPSMClient.getGlobalData().clearMapRoomInvitation();
        minecraft.setScreen(parent);
    }
}
