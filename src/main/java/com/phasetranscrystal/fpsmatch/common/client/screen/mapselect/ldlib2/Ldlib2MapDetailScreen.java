package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.CloseMapViewC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.Locale;
import java.util.UUID;

public final class Ldlib2MapDetailScreen extends Ldlib2MapChildScreen {
    private final Label titleLabel;
    private final Label infoLabel;
    private final VirtualScrollerView<MapRoomPlayerInfo> playerList;
    private final Button joinButton;
    private final Button leaveButton;
    private final Button inviteButton;
    private final Button manageButton;

    public Ldlib2MapDetailScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapDetailScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.detail.title"), detail, parent);
        this.titleLabel = parts.titleLabel();
        this.infoLabel = parts.infoLabel();
        this.playerList = parts.playerList();
        this.joinButton = parts.join();
        this.leaveButton = parts.leave();
        this.inviteButton = parts.invite();
        this.manageButton = parts.manage();
        joinButton.setOnClick(event -> onJoinOrTeamManage());
        leaveButton.setOnClick(event -> send(MapRoomActionC2SPacket.Action.LEAVE, new UUID(0L, 0L)));
        inviteButton.setOnClick(event -> FPSMMapSelectScreens.openChild(new Ldlib2MapInviteScreen(this.detail, this)));
        manageButton.setOnClick(event -> FPSMMapSelectScreens.openChild(new Ldlib2MapManageScreen(this.detail, this)));
        parts.back().setOnClick(event -> onClose());
        refreshContent();
    }

    @Override
    protected void onDetailApplied() {
        refreshContent();
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
                summary.gameType().toUpperCase(Locale.ROOT) + " / " + summary.mapName()
                        + "\n" + Component.translatable("gui.fpsm.map_select.players", summary.joinedPlayers(), max).getString()
                        + "\n" + statusText(summary).getString()
                        + "\n" + Component.translatable("gui.fpsm.map_select.detail.area", summary.areaText()).getString()
                        + "\n" + Component.translatable("gui.fpsm.map_select.detail.dimension", summary.dimension()).getString()));
        playerList.setItems(detail.players());
        playerList.refreshVisibleItems();
        boolean joined = summary.currentPlayerJoined() || summary.currentPlayerSpectating();
        boolean canJoin = !joined && !summary.full() && (!summary.started() || summary.allowJoinInProgress());
        boolean teamMode = !isFreeForAll(summary);
        joinButton.setActive(canJoin || (joined && teamMode));
        joinButton.setText(Component.translatable(
                joined && teamMode ? "gui.fpsm.team_manage.button" : "gui.fpsm.map_select.join"));
        leaveButton.setActive(joined);
        inviteButton.setActive(joined);
        manageButton.setActive(summary.currentPlayerOp());
        manageButton.setVisible(summary.currentPlayerOp());
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
        if (summary.started()) return Component.translatable(summary.allowJoinInProgress()
                ? "gui.fpsm.map_select.status.started_joinable" : "gui.fpsm.map_select.status.started");
        return Component.translatable("gui.fpsm.map_select.status.waiting");
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_detail.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMLdlib2Theme.root(root);
        Label header = label("fpsmatch.map_detail.header", Component.translatable("gui.fpsm.map_select.detail.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(12).height(20));
        FPSMLdlib2Theme.title(header);
        Label titleLabel = label("fpsmatch.map_detail.title", Component.empty());
        titleLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(36).height(18));
        FPSMLdlib2Theme.sectionTitle(titleLabel);
        UIElement panel = new UIElement().setId("fpsmatch.map_detail.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(58).bottom(56));
        FPSMLdlib2Theme.panel(panel);
        Label infoLabel = label("fpsmatch.map_detail.info", Component.empty());
        infoLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(12).height(96));
        FPSMLdlib2Theme.body(infoLabel);
        VirtualScrollerView<MapRoomPlayerInfo> players = new VirtualScrollerView<>();
        players.setId("fpsmatch.map_detail.players");
        players.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(12).right(12).top(114).bottom(12));
        players.virtualScrollerViewStyle(style -> style.estimatedItemHeight(24f));
        players.setItemUIProvider(player -> {
            Label row = label("fpsmatch.map_detail.player." + player.uuid(), Component.literal(
                    player.name() + "  [" + player.teamName() + "]"
                            + (player.spectator() ? " SPEC" : "")
                            + (player.ready() ? " *" : "")));
            row.layout(layout -> layout.height(22).widthPercent(100).paddingLeft(8));
            FPSMLdlib2Theme.muted(row);
            return row;
        });
        panel.addChildren(infoLabel, players);
        Button join = action("fpsmatch.map_detail.join", "gui.fpsm.map_select.join", 18);
        Button leave = action("fpsmatch.map_detail.leave", "gui.fpsm.map_select.leave", 112);
        Button invite = action("fpsmatch.map_detail.invite", "gui.fpsm.map_select.invite", 206);
        Button manage = action("fpsmatch.map_detail.manage", "gui.fpsm.map_select.manage", 300);
        Button back = action("fpsmatch.map_detail.back", "gui.back", 394);
        FPSMLdlib2Theme.button(join, FPSMLdlib2Theme.ButtonKind.PRIMARY);
        FPSMLdlib2Theme.button(leave, FPSMLdlib2Theme.ButtonKind.DANGER);
        FPSMLdlib2Theme.button(invite, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        FPSMLdlib2Theme.button(manage, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        FPSMLdlib2Theme.button(back, FPSMLdlib2Theme.ButtonKind.QUIET);
        root.addChildren(header, titleLabel, panel, join, leave, invite, manage, back);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                titleLabel, infoLabel, players, join, leave, invite, manage, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private static Button action(String id, String key, int left) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(key));
        button.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(left).bottom(16).width(88).height(24));
        return button;
    }

    private record Parts(ModularUI ui, Label titleLabel, Label infoLabel, VirtualScrollerView<MapRoomPlayerInfo> playerList,
                         Button join, Button leave, Button invite, Button manage, Button back) {}
}
