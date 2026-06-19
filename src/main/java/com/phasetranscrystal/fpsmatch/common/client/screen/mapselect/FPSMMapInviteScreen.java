package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FPSMMapInviteScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 380;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 72;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<Button> inviteButtons = new ArrayList<>();
    private ScrollableList list;

    public FPSMMapInviteScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.invite.title"));
        this.detail = detail;
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        inviteButtons.clear();
        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = height - 60;
        // 为每个可邀请玩家创建邀请按钮（全部创建，超出可见范围的由 ScrollableList 控制可见性）
        for (int i = 0; i < detail.availableInviteTargets().size(); i++) {
            MapRoomPlayerInfo player = detail.availableInviteTargets().get(i);
            Button inviteButton = createSmallButton(Component.translatable("gui.fpsm.map_select.invite"), left + PANEL_WIDTH - 80, LIST_TOP + i * ROW_HEIGHT + 2,
                    button -> FPSMatch.sendToServer(new MapRoomActionC2SPacket(MapRoomActionC2SPacket.Action.INVITE, detail.summary().gameType(), detail.summary().mapName(), player.uuid())));
            addRenderableWidget(inviteButton);
            inviteButtons.add(inviteButton);
        }
        addRenderableWidget(createBackButton(button -> onClose()));

        // 创建可滚动列表
        list = new ScrollableList(left, LIST_TOP, PANEL_WIDTH, bottom, ROW_HEIGHT, 0) {
            @Override
            public int totalItems() {
                return detail.availableInviteTargets().size();
            }

            @Override
            protected void renderRow(GuiGraphics graphics, int index, int rowTop, int mouseX, int mouseY) {
                MapRoomPlayerInfo player = detail.availableInviteTargets().get(index);
                boolean hovered = mouseX >= left && mouseX <= left + PANEL_WIDTH && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
                drawRowBackground(graphics, left, rowTop, left + PANEL_WIDTH, rowTop + ROW_HEIGHT, false, hovered, false);
                drawClippedString(graphics, Component.literal(player.name()), left + 8, rowTop + 8, FPSMGuiTheme.TEXT_HIGHLIGHT, 148);
                drawStatusChip(graphics, Component.translatable("gui.fpsm.map_select.online"), left + 168, rowTop + 5, FPSMGuiTheme.ST_ONLINE);
                Button btn = inviteButtons.get(index);
                btn.setX(left + PANEL_WIDTH - 80);
                btn.setY(rowTop + 2);
                btn.visible = true;
            }
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);
        drawScreenTitle(graphics, title, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), 24);

        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = height - 60;
        drawListBackground(graphics, left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6);

        if (detail.availableInviteTargets().isEmpty()) {
            drawEmptyState(graphics, Component.translatable("gui.fpsm.map_select.invite.empty"), width / 2, LIST_TOP + 42);
        } else {
            // 先隐藏所有按钮，由 list.render 重新设置可见性
            inviteButtons.forEach(b -> b.visible = false);
            list.render(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (list != null && list.handleMouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
