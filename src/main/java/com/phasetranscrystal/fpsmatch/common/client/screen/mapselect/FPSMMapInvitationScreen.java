package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgetScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.FPSMWidgets;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class FPSMMapInvitationScreen extends FPSMWidgetScreen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 104;

    private final MapRoomInvitationS2CPacket packet;

    public FPSMMapInvitationScreen(MapRoomInvitationS2CPacket packet) {
        super(Component.translatable("gui.fpsm.map_select.invitation.title"));
        this.packet = packet;
    }

    @Override
    protected void buildUI() {
        int centerX = width / 2;
        int left = centerX - PANEL_WIDTH / 2;
        int panelY = height / 2 - PANEL_HEIGHT / 2;

        // 背景面板
        root.addWidget(new WidgetGroup(left, panelY, PANEL_WIDTH, PANEL_HEIGHT)
                .setBackground(new ColorRectTexture(0xDD222222)));
        root.addWidget(new WidgetGroup(left + 1, panelY + 1, PANEL_WIDTH - 2, PANEL_HEIGHT - 2)
                .setBackground(new ColorRectTexture(0xDD444444)));
        root.addWidget(new WidgetGroup(left + 3, panelY + 3, PANEL_WIDTH - 6, PANEL_HEIGHT - 6)
                .setBackground(new ColorRectTexture(0xDD333333)));

        // 标题
        root.addWidget(new LabelWidget(centerX - font.width(title) / 2, panelY + 10, title.getString()).setTextColor(0xFFFFFFFF));

        // 邀请信息
        root.addWidget(new LabelWidget(centerX - font.width(packet.message()) / 2, panelY + 30,
                packet.message().getString()).setTextColor(0xFFE6F2FF));

        // 接受按钮
        root.addWidget(FPSMWidgets.button(centerX - 90, panelY + 60, 80, 20,
                Component.translatable("gui.fpsm.map_select.accept"),
                () -> {
                    FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                            MapRoomActionC2SPacket.Action.ACCEPT_INVITE,
                            packet.gameType(), packet.mapName(), new UUID(0L, 0L)));
                    FPSMClient.getGlobalData().clearMapRoomInvitation();
                    onClose();
                }));

        // 拒绝按钮
        root.addWidget(FPSMWidgets.button(centerX + 10, panelY + 60, 80, 20,
                Component.translatable("gui.fpsm.map_select.reject"),
                () -> {
                    FPSMClient.getGlobalData().clearMapRoomInvitation();
                    onClose();
                }));
    }

    @Override
    public void onClose() { minecraft.setScreen(null); }
}