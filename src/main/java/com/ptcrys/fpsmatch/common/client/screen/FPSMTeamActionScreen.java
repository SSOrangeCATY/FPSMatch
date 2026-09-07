package com.ptcrys.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2.FPSMMapSelectTheme;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2.Ldlib2MapChildScreen;
import com.ptcrys.fpsmatch.common.client.screen.team.TeamActionModel;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Focused player-action sheet opened from the room roster. */
public final class FPSMTeamActionScreen extends Ldlib2MapChildScreen {
    private final UUID player;
    private final UIElement panel;
    private final Label roomLabel;
    private final Label playerLabel;
    private final VirtualScrollerView<ActionItem> actionList;
    private final AccessibleButton cancelButton;
    private List<ActionItem> actionItems = List.of();
    private boolean compact;
    private boolean initialized;

    public FPSMTeamActionScreen(MapRoomDetail detail, Screen parent, MapRoomPlayerInfo player) {
        this(build(), detail, parent, player.uuid());
    }

    private FPSMTeamActionScreen(Parts parts, MapRoomDetail detail, Screen parent, UUID player) {
        super(parts.ui(), Component.translatable("gui.fpsm.team_manage.context.title"), detail, parent);
        this.player = player;
        this.panel = parts.panel();
        this.roomLabel = parts.room();
        this.playerLabel = parts.player();
        this.actionList = parts.actions();
        this.cancelButton = parts.cancel();
        actionList.setItemUIProvider(this::actionRow);
        cancelButton.setOnClick(event -> onClose());
        registerFocusGroup(this::focusTargets);
    }

    @Override
    public void init() {
        super.init();
        initialized = true;
        compact = width < 420 || height < 270;
        rebuildActions();
    }

    @Override
    protected void onDetailApplied() {
        if (initialized) {
            rebuildActions();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.drawMapIndex(graphics, width, height);
    }

    private void rebuildActions() {
        MapRoomPlayerInfo target = detail.players().stream()
                .filter(candidate -> candidate.uuid().equals(player))
                .findFirst()
                .orElse(null);
        if (target == null) {
            onClose();
            return;
        }

        roomLabel.setValue(Component.literal(
                detail.summary().gameType() + " / " + detail.summary().mapName()));
        playerLabel.setValue(Component.translatable(
                "gui.fpsm.team_manage.context.player", target.name()));

        List<ActionItem> next = new ArrayList<>();
        for (String team : TeamActionModel.availableTargetTeams(detail, player)) {
            String targetTeam = team;
            next.add(new ActionItem(
                    "move." + team,
                    Component.translatable("gui.fpsm.team_manage.context.move",
                            team.toUpperCase(Locale.ROOT)),
                    FPSMMapSelectTheme.ButtonKind.SECONDARY,
                    () -> move(targetTeam)
            ));
        }
        if (TeamActionModel.canKick(detail, player)) {
            next.add(new ActionItem(
                    "kick",
                    Component.translatable("gui.fpsm.map_select.kick"),
                    FPSMMapSelectTheme.ButtonKind.DANGER,
                    this::kick
            ));
        }
        actionItems = List.copyOf(next);
        actionList.setItems(actionItems);
        applyResponsiveLayout();
        actionList.refreshVisibleItems();
        accessibility().reconcileFocus();
    }

    private UIElement actionRow(ActionItem action) {
        AccessibleButton button = new AccessibleButton();
        button.setId("fpsmatch.team_action." + action.id());
        button.setText(action.label());
        button.setAccessibleName(action.label());
        button.setOnClick(event -> action.action().run());
        button.layout(layout -> layout.widthPercent(100)
                .height(compact ? 22 : 26)
                .marginBottom(4));
        FPSMMapSelectTheme.button(button, action.kind());
        button.textStyle(style -> style.fontSize(compact ? 9 : 10));
        return button;
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        actionList.allChildrenStream()
                .filter(AccessibleButton.class::isInstance)
                .map(AccessibleButton.class::cast)
                .filter(UIElement::isActive)
                .forEach(targets::add);
        if (cancelButton.isActive()) {
            targets.add(cancelButton);
        }
        return List.copyOf(targets);
    }

    private void applyResponsiveLayout() {
        compact = width < 420 || height < 270;
        int margin = compact ? 12 : 24;
        int panelWidth = Math.max(1, Math.min(compact ? 320 : 360, width - margin * 2));
        int headerHeight = compact ? 72 : 78;
        int footerHeight = compact ? 36 : 42;
        int rowPitch = compact ? 26 : 30;
        int visibleRows = Math.max(1, Math.min(actionItems.size(), compact ? 4 : 6));
        int desiredHeight = headerHeight + visibleRows * rowPitch + footerHeight;
        int panelHeight = Math.max(1, Math.min(desiredHeight, height - margin * 2));
        int panelLeft = Math.max(0, (width - panelWidth) / 2);
        int panelTop = Math.max(0, (height - panelHeight) / 2);

        absolute(panel, panelLeft, panelTop, panelWidth, panelHeight);
        absolute(roomLabel, 12, 39, Math.max(1, panelWidth - 24), 12);
        absolute(playerLabel, 12, 54, Math.max(1, panelWidth - 24), 13);
        absolute(actionList, 10, headerHeight, Math.max(1, panelWidth - 20),
                Math.max(1, panelHeight - headerHeight - footerHeight));
        absolute(cancelButton, Math.max(10, panelWidth - 122),
                Math.max(headerHeight, panelHeight - footerHeight + 6),
                Math.min(112, Math.max(1, panelWidth - 20)), compact ? 22 : 26);

        actionList.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(rowPitch)
                .overscanPixels(rowPitch * 2));
    }

    private void move(String team) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                MapRoomActionC2SPacket.Action.SWITCH_TEAM,
                detail.summary().gameType(), detail.summary().mapName(), player, team));
        onClose();
    }

    private void kick() {
        if (!TeamActionModel.canKick(detail, player)) {
            return;
        }
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                MapRoomActionC2SPacket.Action.KICK,
                detail.summary().gameType(), detail.summary().mapName(), player));
        onClose();
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.team_action.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);

        UIElement panel = new UIElement().setId("fpsmatch.team_action.panel");
        FPSMMapSelectTheme.elevated(panel);

        Label system = label("fpsmatch.team_action.system",
                Component.translatable("gui.fpsm.map_select.title"));
        FPSMMapSelectTheme.systemLabel(system);
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(7).height(10));

        Label title = label("fpsmatch.team_action.title",
                Component.translatable("gui.fpsm.team_manage.context.title"));
        FPSMMapSelectTheme.title(title);
        title.textStyle(style -> style.fontSize(15).textWrap(TextWrap.HIDE));
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(12).right(12).top(18).height(19));

        Label room = label("fpsmatch.team_action.room", Component.empty());
        FPSMMapSelectTheme.mapIdentity(room);
        room.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));

        Label player = label("fpsmatch.team_action.player", Component.empty());
        FPSMMapSelectTheme.body(player);
        player.textStyle(style -> style.fontSize(10).textWrap(TextWrap.HIDE));

        UIElement divider = new UIElement().setId("fpsmatch.team_action.divider");
        divider.style(style -> style.background(FPSMMapSelectTheme.panelTexture(
                FPSMMapSelectTheme.BORDER_SOFT, FPSMMapSelectTheme.BORDER_SOFT)));
        divider.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(10).right(10).top(70).height(1));

        VirtualScrollerView<ActionItem> actions = new VirtualScrollerView<>();
        actions.setId("fpsmatch.team_action.actions");
        FPSMMapSelectTheme.virtualScroller(actions);

        AccessibleButton cancel = new AccessibleButton();
        cancel.setId("fpsmatch.team_action.cancel");
        cancel.setText(Component.translatable("gui.fpsm.team_manage.cancel"));
        cancel.setAccessibleName(Component.translatable("gui.fpsm.team_manage.cancel"));
        FPSMMapSelectTheme.button(cancel, FPSMMapSelectTheme.ButtonKind.QUIET);
        cancel.textStyle(style -> style.fontSize(9));

        panel.addChildren(system, title, room, player, divider, actions, cancel);
        root.addChild(panel);
        return new Parts(ModularUI.of(UI.of(root,
                size -> Size.of(size.getWidth(), size.getHeight()))),
                panel, room, player, actions, cancel);
    }

    private static Label label(String id, Component value) {
        Label label = new Label();
        label.setId(id);
        label.setValue(value);
        label.setAllowHitTest(false);
        label.setFocusable(false);
        return label;
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
    }

    private record ActionItem(
            String id,
            Component label,
            FPSMMapSelectTheme.ButtonKind kind,
            Runnable action
    ) {
    }

    private record Parts(
            ModularUI ui,
            UIElement panel,
            Label room,
            Label player,
            VirtualScrollerView<ActionItem> actions,
            AccessibleButton cancel
    ) {
    }
}
