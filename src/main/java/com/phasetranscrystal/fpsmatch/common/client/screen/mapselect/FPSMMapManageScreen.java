package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * 地图管理界面。
 * <p>
 * 聚合调试操作、地图设置与商店编辑入口，不再显示玩家列表
 *（玩家与队伍操作已迁移至队伍管理界面）。
 */
public class FPSMMapManageScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int HEADER_Y = 74;
    private static final int SECOND_ROW_Y = 100;

    private MapRoomDetail detail;
    private final Screen parent;

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
        int centerX = width / 2;
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;

        // 顶部调试按钮行：开始/重置/新回合/清理/调试
        int debugTotal = 5;
        int[] debugXs = new int[debugTotal];
        for (int i = 0; i < debugTotal; i++) {
            debugXs[i] = buttonX(centerX, bw, gap, i, debugTotal);
        }
        boolean op = detail.summary().currentPlayerOp();
        Button startButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.start"), debugXs[0], HEADER_Y,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_START, new UUID(0L, 0L))));
        Button resetButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.reset"), debugXs[1], HEADER_Y,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_RESET, new UUID(0L, 0L))));
        Button newRoundButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.new_round"), debugXs[2], HEADER_Y,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_NEW_ROUND, new UUID(0L, 0L))));
        Button cleanupButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.cleanup"), debugXs[3], HEADER_Y,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_CLEANUP, new UUID(0L, 0L))));
        Button switchButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.debug.switch"), debugXs[4], HEADER_Y,
                button -> sendAction(MapRoomActionC2SPacket.Action.DEBUG_SWITCH, new UUID(0L, 0L))));
        startButton.active = op;
        resetButton.active = op;
        newRoundButton.active = op;
        cleanupButton.active = op;
        switchButton.active = op;

        // 第二行：设置 / 编辑商店
        int secondTotal = 2;
        int[] secondXs = new int[secondTotal];
        for (int i = 0; i < secondTotal; i++) {
            secondXs[i] = buttonX(centerX, bw, gap, i, secondTotal);
        }
        Button settingsButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.settings"), secondXs[0], SECOND_ROW_Y,
                button -> FPSMMapSelectScreens.openChild(new FPSMMapSettingsScreen(detail, this))));
        Button shopButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_detail.edit_shop"), secondXs[1], SECOND_ROW_Y,
                button -> FPSMMapSelectScreens.openChild(new FPSMMapShopScreen(detail, this))));
        settingsButton.active = op;
        shopButton.active = op && !detail.editableShops().isEmpty();

        addRenderableWidget(createBackButton(button -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, FPSMGuiTheme.TEXT_TITLE);
        graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 48, FPSMGuiTheme.TEXT_SUB);

        if (!detail.summary().currentPlayerOp()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fpsm.map_select.manage.no_permission"), width / 2, HEADER_Y + 60, FPSMGuiTheme.TEXT_MUTED);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
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
