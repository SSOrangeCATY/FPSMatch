package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

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
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Responsive, keyboard-accessible player picker for room invitations. */
public final class Ldlib2MapInviteScreen extends Ldlib2MapChildScreen {
    private final Label systemLabel;
    private final Label headerLabel;
    private final Label subtitleLabel;
    private final UIElement panel;
    private final VirtualScrollerView<MapRoomPlayerInfo> list;
    private final Label emptyLabel;
    private final AccessibleButton backButton;
    private boolean compact;

    public Ldlib2MapInviteScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapInviteScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.invite.title"), detail, parent);
        this.systemLabel = parts.system();
        this.headerLabel = parts.header();
        this.subtitleLabel = parts.subtitle();
        this.panel = parts.panel();
        this.list = parts.list();
        this.emptyLabel = parts.empty();
        this.backButton = parts.back();
        list.setItemUIProvider(this::playerRow);
        backButton.setOnClick(event -> onClose());
        backButton.setAccessibleState(() -> this.detail.availableInviteTargets().isEmpty()
                ? Component.translatable("gui.fpsm.map_select.invite.empty")
                : Component.empty());
        registerFocusGroup(this::focusTargets);
        refreshContent();
    }

    @Override
    public void init() {
        super.init();
        applyResponsiveLayout();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.drawMapIndex(graphics, this.width, this.height);
    }

    @Override
    protected void onDetailApplied() {
        refreshContent();
    }

    private void refreshContent() {
        subtitleLabel.setValue(Component.literal(
                detail.summary().gameType() + " / " + detail.summary().mapName()));
        List<MapRoomPlayerInfo> targets = detail.availableInviteTargets();
        boolean empty = targets.isEmpty();
        list.setItems(targets);
        list.setVisible(!empty);
        list.refreshVisibleItems();
        emptyLabel.setVisible(empty);
        accessibility().reconcileFocus();
    }

    private UIElement playerRow(MapRoomPlayerInfo player) {
        UIElement row = new UIElement().setId("fpsmatch.map_invite.row." + player.uuid());
        row.layout(layout -> layout.widthPercent(100).height(compact ? 46 : 34));

        AccessibleButton invite = (AccessibleButton) new AccessibleButton().noText();
        invite.setId("fpsmatch.map_invite.btn." + player.uuid());
        invite.setAccessibleName(() -> Component.translatable("gui.fpsm.map_select.invite")
                .copy().append(" ").append(player.name()));
        invite.setAccessibleState(() -> Component.translatable("gui.fpsm.map_select.online"));
        invite.setOnClick(event -> sendInvite(player.uuid()));
        invite.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).top(0).bottom(0));
        FPSMMapSelectTheme.roomRow(invite, FPSMMapSelectTheme.SUCCESS, false);

        Label name = label("fpsmatch.map_invite.name." + player.uuid(),
                Component.literal(player.name()));
        FPSMMapSelectTheme.body(name);
        name.textStyle(style -> style.fontSize(10).textWrap(TextWrap.HIDE));

        Label online = label("fpsmatch.map_invite.online." + player.uuid(),
                Component.translatable("gui.fpsm.map_select.online"));
        FPSMMapSelectTheme.status(online, FPSMMapSelectTheme.SUCCESS);
        online.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));

        Label action = label("fpsmatch.map_invite.action." + player.uuid(),
                Component.translatable("gui.fpsm.map_select.invite"));
        FPSMMapSelectTheme.status(action, FPSMMapSelectTheme.ACCENT);
        action.textStyle(style -> style.fontSize(10).textWrap(TextWrap.HIDE));

        if (compact) {
            inset(name, 10, 88, 5, 15);
            inset(online, 10, 88, 25, 13);
            right(action, 10, 16, 68, 14);
        } else {
            inset(name, 12, 178, 10, 14);
            right(online, 92, 10, 76, 14);
            right(action, 12, 10, 68, 14);
        }
        invite.addChildren(name, online, action);
        row.addChild(invite);
        return row;
    }

    private void sendInvite(UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                MapRoomActionC2SPacket.Action.INVITE,
                detail.summary().gameType(),
                detail.summary().mapName(),
                target));
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        List<Ldlib2AccessibilityController.FocusTarget> targets = new ArrayList<>();
        list.allChildrenStream()
                .filter(AccessibleButton.class::isInstance)
                .map(AccessibleButton.class::cast)
                .filter(UIElement::isActive)
                .forEach(targets::add);
        if (backButton.isActive()) {
            targets.add(backButton);
        }
        return List.copyOf(targets);
    }

    private void applyResponsiveLayout() {
        compact = width < 420 || height < 270;
        int margin = compact ? 8 : 16;
        int contentWidth = Math.max(1, width - margin * 2);
        int panelTop = compact ? 52 : 58;
        int footerHeight = compact ? 38 : 46;
        int panelHeight = Math.max(1, height - panelTop - footerHeight);

        absolute(systemLabel, margin + 2, 3, contentWidth - 4, 10);
        absolute(headerLabel, margin + 2, 14, contentWidth - 4, 18);
        absolute(subtitleLabel, margin + 2, 34, contentWidth - 4, 14);
        absolute(panel, margin, panelTop, contentWidth, panelHeight);
        absolute(emptyLabel, 12, 16, Math.max(1, contentWidth - 24), 32);
        absolute(list, 6, 6, Math.max(1, contentWidth - 12), Math.max(1, panelHeight - 12));
        absolute(backButton, margin, height - footerHeight + 7,
                Math.min(112, contentWidth), 26);

        list.virtualScrollerViewStyle(style -> style
                .estimatedItemHeight(compact ? 48f : 36f)
                .overscanPixels(48));
        list.refreshVisibleItems();
        accessibility().reconcileFocus();
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_invite.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);

        Label system = label("fpsmatch.map_invite.system",
                Component.translatable("gui.fpsm.map_select.title"));
        FPSMMapSelectTheme.systemLabel(system);

        Label header = label("fpsmatch.map_invite.header",
                Component.translatable("gui.fpsm.map_select.invite.title"));
        FPSMMapSelectTheme.title(header);

        Label subtitle = label("fpsmatch.map_invite.subtitle", Component.empty());
        FPSMMapSelectTheme.mapIdentity(subtitle);
        subtitle.textStyle(style -> style.fontSize(10).textWrap(TextWrap.HIDE));

        UIElement panel = new UIElement().setId("fpsmatch.map_invite.panel");
        FPSMMapSelectTheme.panel(panel);

        Label empty = label("fpsmatch.map_invite.empty",
                Component.translatable("gui.fpsm.map_select.invite.empty"));
        FPSMMapSelectTheme.muted(empty);
        empty.textStyle(style -> style.fontSize(10).textWrap(TextWrap.WRAP));

        VirtualScrollerView<MapRoomPlayerInfo> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_invite.list");
        FPSMMapSelectTheme.virtualScroller(list);
        panel.addChildren(empty, list);

        AccessibleButton back = new AccessibleButton();
        back.setId("fpsmatch.map_invite.back");
        back.setText(Component.translatable("gui.back"));
        FPSMMapSelectTheme.button(back, FPSMMapSelectTheme.ButtonKind.QUIET);
        back.textStyle(style -> style.fontSize(10));

        root.addChildren(system, header, subtitle, panel, back);
        return new Parts(ModularUI.of(UI.of(root,
                size -> Size.of(size.getWidth(), size.getHeight()))),
                system, header, subtitle, panel, list, empty, back);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        label.setAllowHitTest(false);
        label.setFocusable(false);
        return label;
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
    }

    private static void inset(UIElement element, int left, int right, int top, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .bottomAuto().left(left).right(right).top(top).height(height));
    }

    private static void right(UIElement element, int right, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .leftAuto().bottomAuto().right(right).top(top).width(width).height(height));
    }

    private record Parts(
            ModularUI ui,
            Label system,
            Label header,
            Label subtitle,
            UIElement panel,
            VirtualScrollerView<MapRoomPlayerInfo> list,
            Label empty,
            AccessibleButton back
    ) {
    }
}
