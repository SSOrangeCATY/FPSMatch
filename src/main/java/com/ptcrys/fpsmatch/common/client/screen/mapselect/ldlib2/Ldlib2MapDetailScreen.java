package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.packet.mapselect.CloseMapViewC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Ldlib2MapDetailScreen extends Ldlib2MapChildScreen {

    private final Label systemLabel;
    private final Label headerLabel;
    private final Label titleLabel;
    private final Label infoLabel;
    private final UIElement panel;
    private final VirtualScrollerView<MapRoomPlayerInfo> playerList;
    private final AccessibleButton joinButton;
    private final AccessibleButton leaveButton;
    private final AccessibleButton inviteButton;
    private final AccessibleButton manageButton;
    private final AccessibleButton backButton;

    public Ldlib2MapDetailScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapDetailScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.detail.title"), detail, parent);
        this.systemLabel = parts.systemLabel();
        this.headerLabel = parts.headerLabel();
        this.titleLabel = parts.titleLabel();
        this.infoLabel = parts.infoLabel();
        this.panel = parts.panel();
        this.playerList = parts.playerList();
        this.joinButton = parts.join();
        this.leaveButton = parts.leave();
        this.inviteButton = parts.invite();
        this.manageButton = parts.manage();
        this.backButton = parts.back();
        joinButton.setOnClick(event -> onJoinOrTeamManage());
        leaveButton.setOnClick(event -> send(MapRoomActionC2SPacket.Action.LEAVE, new UUID(0L, 0L)));
        inviteButton.setOnClick(event -> FPSMMapSelectScreens.openChild(new Ldlib2MapInviteScreen(this.detail, this)));
        manageButton.setOnClick(event -> FPSMMapSelectScreens.openChild(new Ldlib2MapManageScreen(this.detail, this)));
        backButton.setOnClick(event -> onClose());
        registerFocusGroup(this::focusTargets);
        refreshContent();
    }

    @Override
    public void init() {
        super.init();
        applyResponsiveLayout();
    }

    @Override
    protected void onDetailApplied() {
        refreshContent();
        if (width > 0 && height > 0) {
            applyResponsiveLayout();
        }
    }

    @Override
    public void onClose() {
        if (parent instanceof Ldlib2MapSelectionScreen) {
            FPSMatch.sendToServer(new OpenMapSelectionC2SPacket());
        } else {
            FPSMatch.sendToServer(new CloseMapViewC2SPacket());
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshContent() {
        MapRoomSummary summary = detail.summary();
        titleLabel.setValue(Component.literal(summary.displayName()));
        String max = summary.maxPlayers() < 0 ? "?" : Integer.toString(summary.maxPlayers());
        infoLabel.setValue(Component.literal(
                summary.gameType().toUpperCase(Locale.ROOT) + " / " + summary.mapName() + "\n" + Component.translatable("gui.fpsm.map_select.players", summary.joinedPlayers(), max).getString() + "\n" + statusText(summary).getString() + "\n" + Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()).getString() + "\n" + Component.translatable("gui.fpsm.map_select.detail.dimension", summary.dimension()).getString()));
        playerList.setItems(detail.players());
        playerList.refreshVisibleItems();
        boolean joined = summary.currentPlayerJoined() || summary.currentPlayerSpectating();
        boolean canJoin = !joined && !summary.full() && (!summary.started() || summary.allowJoinInProgress());
        boolean teamMode = !isFreeForAll(summary);
        joinButton.setText(Component.translatable(
                joined && teamMode ? "gui.fpsm.team_manage.button" : "gui.fpsm.map_select.join"));
        FPSMLdlib2Theme.buttonState(joinButton, FPSMLdlib2Theme.ButtonKind.PRIMARY,
                canJoin || (joined && teamMode));
        FPSMLdlib2Theme.buttonState(leaveButton, FPSMLdlib2Theme.ButtonKind.DANGER, joined);
        FPSMLdlib2Theme.buttonState(inviteButton, FPSMLdlib2Theme.ButtonKind.SECONDARY,
                joined && !detail.availableInviteTargets().isEmpty());
        FPSMLdlib2Theme.buttonState(manageButton, FPSMLdlib2Theme.ButtonKind.SECONDARY,
                summary.currentPlayerOp());
        manageButton.setVisible(summary.currentPlayerOp());
        if (width > 0 && height > 0) {
            layoutActions(width < 520 || height < 280);
        }
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        for (AccessibleButton button : List.of(
                joinButton, leaveButton, inviteButton, manageButton, backButton)) {
            if (button.isVisible() && button.isActive()) {
                targets.add(button);
            }
        }
        return List.copyOf(targets);
    }

    private void applyResponsiveLayout() {
        boolean compact = width < 520 || height < 280;
        int margin = Math.min(16, Math.max(8, width / 32));
        int contentWidth = Math.max(1, width - margin * 2);
        placeRoot(systemLabel, margin + 2, 2, Math.max(1, contentWidth - 4), 10);
        placeRoot(headerLabel, margin + 2, 12, Math.max(1, contentWidth - 4), 20);
        placeRoot(titleLabel, margin + 2, 36, Math.max(1, contentWidth - 4), 18);

        int panelTop = 58;
        int actionRowsHeight = compact ? 44 : 24;
        int panelBottom = 8 + actionRowsHeight + 8;
        placeRoot(panel, margin, panelTop, contentWidth,
                Math.max(1, height - panelTop - panelBottom));

        int infoTop = compact ? 8 : 12;
        int infoHeight = compact ? 62 : 96;
        infoLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(infoTop).height(infoHeight));
        playerList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(infoTop + infoHeight + 4).bottom(8));
        layoutActions(compact);
    }

    private void layoutActions(boolean compact) {
        int margin = Math.min(16, Math.max(8, width / 32));
        int contentWidth = Math.max(1, width - margin * 2);
        if (!compact) {
            layoutActionRow(List.of(joinButton, leaveButton, inviteButton, manageButton, backButton),
                    margin, height - 32, contentWidth, 24);
            return;
        }
        layoutActionRow(List.of(joinButton, leaveButton, inviteButton),
                margin, height - 52, contentWidth, 20);
        List<AccessibleButton> secondRow = manageButton.isVisible() ? List.of(manageButton, backButton) : List.of(backButton);
        layoutActionRow(secondRow, margin, height - 28, contentWidth, 20);
    }

    private static void layoutActionRow(
                                        List<AccessibleButton> buttons, int left, int top, int width, int height) {
        int gap = 6;
        int buttonWidth = Math.max(1, (width - gap * Math.max(0, buttons.size() - 1)) / buttons.size());
        for (int index = 0; index < buttons.size(); index++) {
            AccessibleButton button = buttons.get(index);
            placeRoot(button, left + index * (buttonWidth + gap), top, buttonWidth, height);
        }
    }

    private static void placeRoot(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top).width(width).height(height));
    }

    private void onJoinOrTeamManage() {
        MapRoomSummary summary = detail.summary();
        if (isFreeForAll(summary) && !summary.currentPlayerJoined()) {
            send(MapRoomActionC2SPacket.Action.JOIN, new UUID(0L, 0L));
            return;
        }
        if (!isFreeForAll(summary)) {
            FPSMMapSelectScreens.openChild(new Ldlib2TeamManageScreen(detail, this));
            return;
        }
        send(MapRoomActionC2SPacket.Action.JOIN, new UUID(0L, 0L));
    }

    private void send(MapRoomActionC2SPacket.Action action, UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
    }

    private static boolean isFreeForAll(MapRoomSummary summary) {
        return "csdm".equalsIgnoreCase(summary.gameType());
    }

    private static Component statusText(MapRoomSummary summary) {
        if (summary.full()) return Component.translatable("gui.fpsm.map_select.full");
        if (summary.debug()) return Component.translatable("gui.fpsm.map_select.status.debug");
        if (summary.started()) return Component.translatable(summary.allowJoinInProgress() ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_detail.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);
        Label system = label("fpsmatch.map_detail.system", Component.literal("FPSM // MAP SYSTEM  ·  ROOM DOSSIER"));
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(2).height(10));
        FPSMLdlib2Theme.systemLabel(system);
        Label header = label("fpsmatch.map_detail.header", Component.translatable("gui.fpsm.map_select.detail.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(12).height(20));
        FPSMLdlib2Theme.title(header);
        Label titleLabel = label("fpsmatch.map_detail.title", Component.empty());
        titleLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(36).height(18));
        FPSMLdlib2Theme.mapIdentity(titleLabel);
        UIElement panel = new UIElement().setId("fpsmatch.map_detail.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(58).bottom(56));
        FPSMLdlib2Theme.panel(panel);
        Label infoLabel = label("fpsmatch.map_detail.info", Component.empty());
        infoLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(12).height(96));
        FPSMLdlib2Theme.body(infoLabel);
        VirtualScrollerView<MapRoomPlayerInfo> players = new VirtualScrollerView<>();
        players.setId("fpsmatch.map_detail.players");
        players.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).right(12).top(114).bottom(12));
        FPSMLdlib2Theme.virtualScroller(players);
        players.virtualScrollerViewStyle(style -> style.estimatedItemHeight(24f));
        players.setItemUIProvider(player -> {
            Label row = label("fpsmatch.map_detail.player." + player.uuid(), Component.literal(
                    player.name() + "  [" + player.teamName() + "]" + (player.spectator() ? " SPEC" : "") + (player.ready() ? " *" : "")));
            row.layout(layout -> layout.height(22).widthPercent(100).paddingLeft(8));
            FPSMLdlib2Theme.muted(row);
            return row;
        });
        panel.addChildren(infoLabel, players);
        AccessibleButton join = action("fpsmatch.map_detail.join", "gui.fpsm.map_select.join");
        AccessibleButton leave = action("fpsmatch.map_detail.leave", "gui.fpsm.map_select.leave");
        AccessibleButton invite = action("fpsmatch.map_detail.invite", "gui.fpsm.map_select.invite");
        AccessibleButton manage = action("fpsmatch.map_detail.manage", "gui.fpsm.map_select.manage");
        AccessibleButton back = action("fpsmatch.map_detail.back", "gui.back");
        FPSMLdlib2Theme.button(join, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        FPSMLdlib2Theme.button(leave, FPSMLdlib2Theme.ButtonKind.DANGER);
        FPSMLdlib2Theme.button(invite, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        FPSMLdlib2Theme.button(manage, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);
        root.addChildren(system, header, titleLabel, panel, join, leave, invite, manage, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                system, header, titleLabel, infoLabel, panel, players, join, leave, invite, manage, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private static AccessibleButton action(String id, String key) {
        AccessibleButton button = new AccessibleButton();
        button.setId(id);
        button.setText(Component.translatable(key));
        return button;
    }

    private record Parts(
                         ModularUI ui, Label systemLabel, Label headerLabel, Label titleLabel, Label infoLabel,
                         UIElement panel, VirtualScrollerView<MapRoomPlayerInfo> playerList,
                         AccessibleButton join, AccessibleButton leave, AccessibleButton invite,
                         AccessibleButton manage, AccessibleButton back) {}
}
