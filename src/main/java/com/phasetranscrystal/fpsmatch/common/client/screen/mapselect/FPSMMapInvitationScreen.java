package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapInvitationScreen extends Screen {
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
        addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.invitation.accept"), button -> accept())
                .bounds(centerX - 104, top + 78, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fpsm.map_select.invitation.reject"), button -> reject())
                .bounds(centerX + 8, top + 78, 96, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - PANEL_WIDTH / 2;
        int top = height / 2 - PANEL_HEIGHT / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xDD101820);
        graphics.drawCenteredString(font, title, width / 2, top + 14, 0xFFFFFFFF);
        graphics.drawCenteredString(font, invitation.message(), width / 2, top + 42, 0xFFE6F2FF);
        graphics.drawCenteredString(font, Component.literal(invitation.gameType() + " / " + invitation.mapName()), width / 2, top + 58, 0xFFB8D4E3);
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
