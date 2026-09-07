package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleModularUIScreen;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2AccessibilityController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2RenderGuard;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;

/** Responsive, keyboard-accessible confirmation layer for an incoming room invitation. */
public final class Ldlib2MapInvitationScreen extends AccessibleModularUIScreen {
    private final MapRoomInvitationS2CPacket invitation;
    private final Screen parent;
    private final Label systemLabel;
    private final UIElement panel;
    private final Label titleLabel;
    private final Label messageLabel;
    private final Label roomLabel;
    private final AccessibleButton acceptButton;
    private final AccessibleButton rejectButton;

    public Ldlib2MapInvitationScreen(MapRoomInvitationS2CPacket invitation, Screen parent) {
        this(build(invitation), invitation, parent);
    }

    private Ldlib2MapInvitationScreen(
            Parts parts,
            MapRoomInvitationS2CPacket invitation,
            Screen parent
    ) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.invitation.title"));
        this.invitation = invitation;
        this.parent = parent;
        this.systemLabel = parts.system();
        this.panel = parts.panel();
        this.titleLabel = parts.title();
        this.messageLabel = parts.message();
        this.roomLabel = parts.room();
        this.acceptButton = parts.accept();
        this.rejectButton = parts.reject();
        acceptButton.setOnClick(event -> accept());
        rejectButton.setOnClick(event -> reject());
        registerFocusGroup(this::focusTargets);
    }

    public Screen parentScreen() {
        return parent;
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
    public void onClose() {
        reject();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } catch (ConcurrentModificationException failure) {
            if (!Ldlib2RenderGuard.ignoreConcurrentModification(this, failure)) {
                throw failure;
            }
        }
    }

    private List<Ldlib2AccessibilityController.FocusTarget> focusTargets() {
        return List.of(acceptButton, rejectButton);
    }

    private void applyResponsiveLayout() {
        int outerMargin = width < 420 ? 8 : 18;
        int panelWidth = Math.max(1, Math.min(420, width - outerMargin * 2));
        boolean stacked = panelWidth < 220 && height >= 200;
        boolean showSystem = height >= 180;
        int topReserve = showSystem ? 24 : 6;
        int bottomReserve = 8;
        int availableHeight = Math.max(1, height - topReserve - bottomReserve);
        int desiredHeight = stacked ? 212 : 180;
        int panelHeight = Math.max(1, Math.min(desiredHeight, availableHeight));
        int panelLeft = Math.max(0, (width - panelWidth) / 2);
        int panelTop = topReserve + Math.max(0, (availableHeight - panelHeight) / 2);
        int inset = panelWidth < 340 ? 12 : 18;

        systemLabel.setVisible(showSystem);
        absolute(systemLabel, outerMargin + 2, 5,
                Math.max(1, width - outerMargin * 2 - 4), 12);
        absolute(panel, panelLeft, panelTop, panelWidth, panelHeight);
        absolute(titleLabel, inset, 14, Math.max(1, panelWidth - inset * 2), 22);

        int messageTop = 44;
        if (stacked) {
            int rejectTop = Math.max(1, panelHeight - 42);
            int acceptTop = Math.max(1, rejectTop - 34);
            int roomTop = Math.max(messageTop, acceptTop - 25);
            absolute(messageLabel, inset, messageTop,
                    Math.max(1, panelWidth - inset * 2), Math.max(1, roomTop - messageTop - 5));
            absolute(roomLabel, inset, roomTop,
                    Math.max(1, panelWidth - inset * 2), 17);
            absolute(acceptButton, inset, acceptTop,
                    Math.max(1, panelWidth - inset * 2), 28);
            absolute(rejectButton, inset, rejectTop,
                    Math.max(1, panelWidth - inset * 2), 28);
        } else {
            int buttonTop = Math.max(1, panelHeight - 42);
            int roomTop = Math.max(messageTop, buttonTop - 25);
            int gap = 8;
            int buttonWidth = Math.max(1, (panelWidth - inset * 2 - gap) / 2);
            absolute(messageLabel, inset, messageTop,
                    Math.max(1, panelWidth - inset * 2), Math.max(1, roomTop - messageTop - 5));
            absolute(roomLabel, inset, roomTop,
                    Math.max(1, panelWidth - inset * 2), 17);
            absolute(acceptButton, inset, buttonTop, buttonWidth, 28);
            absolute(rejectButton, inset + buttonWidth + gap, buttonTop, buttonWidth, 28);
        }
        accessibility().reconcileFocus();
    }

    private void accept() {
        FPSMClient.getGlobalData().clearMapRoomInvitation();
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(
                MapRoomActionC2SPacket.Action.ACCEPT_INVITE,
                invitation.gameType(), invitation.mapName(), new UUID(0L, 0L)));
        Minecraft.getInstance().setScreen(parent);
    }

    private void reject() {
        FPSMClient.getGlobalData().clearMapRoomInvitation();
        Minecraft.getInstance().setScreen(parent);
    }

    private static Parts build(MapRoomInvitationS2CPacket invitation) {
        UIElement root = new UIElement().setId("fpsmatch.map_invitation.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);

        Label system = label("fpsmatch.map_invitation.system",
                Component.translatable("gui.fpsm.map_select.title"));
        FPSMMapSelectTheme.systemLabel(system);
        system.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));

        UIElement panel = new UIElement().setId("fpsmatch.map_invitation.panel");
        FPSMMapSelectTheme.panel(panel);

        Label title = label("fpsmatch.map_invitation.title",
                Component.translatable("gui.fpsm.map_select.invitation.title"));
        FPSMMapSelectTheme.sectionTitle(title);
        title.textStyle(style -> style.fontSize(13).textWrap(TextWrap.HIDE));

        Label message = label("fpsmatch.map_invitation.message", invitation.message());
        FPSMMapSelectTheme.body(message);
        message.textStyle(style -> style.fontSize(10).lineSpacing(2).textWrap(TextWrap.WRAP));

        Label room = label("fpsmatch.map_invitation.room",
                Component.literal(invitation.gameType() + " / " + invitation.mapName()));
        FPSMMapSelectTheme.mapIdentity(room);
        room.textStyle(style -> style.fontSize(10).textWrap(TextWrap.HIDE));

        Component roomIdentity = Component.literal(
                invitation.gameType() + " / " + invitation.mapName());
        AccessibleButton accept = new AccessibleButton();
        accept.setId("fpsmatch.map_invitation.accept");
        accept.setText(Component.translatable("gui.fpsm.map_select.invitation.accept"));
        accept.setAccessibleState(() -> roomIdentity);
        accept.setAccessibleHint(() -> invitation.message());
        FPSMMapSelectTheme.button(accept, FPSMMapSelectTheme.ButtonKind.PRIMARY);
        accept.textStyle(style -> style.fontSize(10));

        AccessibleButton reject = new AccessibleButton();
        reject.setId("fpsmatch.map_invitation.reject");
        reject.setText(Component.translatable("gui.fpsm.map_select.invitation.reject"));
        reject.setAccessibleState(() -> roomIdentity);
        FPSMMapSelectTheme.button(reject, FPSMMapSelectTheme.ButtonKind.SECONDARY);
        reject.textStyle(style -> style.fontSize(10));

        panel.addChildren(title, message, room, accept, reject);
        root.addChildren(system, panel);
        return new Parts(ModularUI.of(UI.of(root,
                size -> Size.of(size.getWidth(), size.getHeight()))),
                system, panel, title, message, room, accept, reject);
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

    private record Parts(
            ModularUI ui,
            Label system,
            UIElement panel,
            Label title,
            Label message,
            Label room,
            AccessibleButton accept,
            AccessibleButton reject
    ) {
    }
}
