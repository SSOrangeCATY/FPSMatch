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
import java.util.UUID;

public class FPSMMapManageScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int PANEL_WIDTH = 440;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 118;

    private MapRoomDetail detail;
    private final Screen parent;
    private final List<Button> kickButtons = new ArrayList<>();
    private ScrollableList list;

    public FPSMMapManageScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.map_select.manage.title"));
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
        kickButtons.clear();
        int centerX = width / 2;
        // 顶部 5 个调试按钮，统一中按钮 90，间距 8
        int total = 5;
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;
        int[] xs = new int[total];
        for (int i = 0; i < total; i++) {
            xs[i] = buttonX(centerX, bw, gap, i, total);
        }
        Button startButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.start"), xs[0], 74,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_START, new UUID(0L, 0L))));
        Button resetButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.reset"), xs[1], 74,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_RESET, new UUID(0L, 0L))));
        Button newRoundButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.new_round"), xs[2], 74,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_NEW_ROUND, new UUID(0L, 0L))));
        Button cleanupButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.cleanup"), xs[3], 74,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_CLEANUP, new UUID(0L, 0L))));
        Button switchButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.switch"), xs[4], 74,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_SWITCH, new UUID(0L, 0L))));
        boolean op = detail.summary().currentPlayerOp();
        startButton.active = op;
        resetButton.active = op;
        newRoundButton.active = op;
        cleanupButton.active = op;
        switchButton.active = op;

        int left = centerX - PANEL_WIDTH / 2;
        int bottom = height - 60;
        // 为每个玩家创建踢出按钮（全部创建，超出可见范围的由 ScrollableList 控制可见性）
        for (int i = 0; i < detail.players().size(); i++) {
            MapRoomPlayerInfo player = detail.players().get(i);
            Button kickButton = createSmallButton(Component.translatable("gui.fpsm.map_select.kick"), left + PANEL_WIDTH - 80, LIST_TOP + i * ROW_HEIGHT + 2,
                    button -> sendAction(MapRoomActionC2SPacket.Action.KICK, player.uuid()));
            kickButton.active = op;
            addRenderableWidget(kickButton);
            kickButtons.add(kickButton);
        }
        addRenderableWidget(createBackButton(button -> onClose()));

        // 创建可滚动列表
        list = new ScrollableList(left, LIST_TOP, PANEL_WIDTH, bottom, ROW_HEIGHT, 0) {
            @Override
            public int totalItems() {
                return detail.players().size();
            }

            @Override
            protected void renderRow(GuiGraphics graphics, int index, int rowTop, int mouseX, int mouseY) {
                MapRoomPlayerInfo player = detail.players().get(index);
                graphics.drawString(font, Component.literal(player.name()), left + 8, rowTop + 8, player.online() ? FPSMGuiTheme.TEXT_HIGHLIGHT : FPSMGuiTheme.TEXT_DISABLED, false);
                graphics.drawString(font, Component.literal(player.teamName()), left + 140, rowTop + 8, player.spectator() ? FPSMGuiTheme.ST_SPECTATOR : FPSMGuiTheme.ST_ONLINE, false);
                graphics.drawString(font, Component.translatable(player.online() ? "gui.fpsm.map_select.online" : "gui.fpsm.map_select.offline"), left + 260, rowTop + 8, player.online() ? FPSMGuiTheme.ST_ONLINE : FPSMGuiTheme.TEXT_DISABLED, false);
                // 重新定位对应按钮
                Button btn = kickButtons.get(index);
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
        graphics.drawCenteredString(font, title, width / 2, 24, FPSMGuiTheme.TEXT_TITLE);
        graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 48, FPSMGuiTheme.TEXT_SUB);

        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = height - 60;
        graphics.drawString(font, Component.translatable("gui.fpsm.map_select.players.title"), left, LIST_TOP - 22, FPSMGuiTheme.TEXT_TITLE, false);
        // 列表面板背景（统一）
        drawListBackground(graphics, left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6);

        if (detail.players().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.players.empty"), width / 2, LIST_TOP + 32, FPSMGuiTheme.TEXT_MUTED);
        } else {
            // 先隐藏所有按钮，由 list.render 重新设置可见性
            kickButtons.forEach(b -> b.visible = false);
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

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
    }
}
