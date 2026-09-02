package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.Ldlib2RenderGuard;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ConcurrentModificationException;
import java.util.UUID;

/** Incoming map-room invitation dialog. */
public final class Ldlib2MapInvitationScreen extends ModularUIScreen {

    private final MapRoomInvitationS2CPacket invitation;
    private final Screen parent;

    public Ldlib2MapInvitationScreen(MapRoomInvitationS2CPacket invitation, Screen parent) {
        this(build(invitation), invitation, parent);
    }

    private Ldlib2MapInvitationScreen(Parts parts, MapRoomInvitationS2CPacket invitation, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.invitation.title"));
        this.invitation = invitation;
        this.parent = parent;
        parts.accept().setOnClick(e -> accept());
        parts.reject().setOnClick(e -> reject());
    }

    public Screen parentScreen() {
        return parent;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.draw(graphics, this.width, this.height);
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
    public void removed() {
        modularUI.onRemoved();
        super.removed();
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
        FPSMLdlib2Theme.root(root);

        Label system = label("fpsmatch.map_invitation.system", Component.literal("FPSM // MAP SYSTEM  ·  INCOMING SIGNAL"));
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(18).right(18).top(2).height(10));
        FPSMLdlib2Theme.systemLabel(system);

        UIElement panel = new UIElement().setId("fpsmatch.map_invitation.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .leftPercent(50).topPercent(50).width(340).height(150)
                .marginLeft(-170).marginTop(-75));
        FPSMLdlib2Theme.panel(panel);

        Label title = label("fpsmatch.map_invitation.title", Component.translatable("gui.fpsm.map_select.invitation.title"));
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(14).height(18));
        FPSMLdlib2Theme.title(title);

        Label message = label("fpsmatch.map_invitation.message", invitation.message());
        message.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(42).height(18));
        FPSMLdlib2Theme.body(message);

        Label room = label("fpsmatch.map_invitation.room",
                Component.literal(invitation.gameType() + " / " + invitation.mapName()));
        room.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(16).right(16).top(64).height(16));
        FPSMLdlib2Theme.muted(room);

        Button accept = new Button();
        accept.setId("fpsmatch.map_invitation.accept");
        accept.setText(Component.translatable("gui.fpsm.map_select.invitation.accept"));
        accept.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(24).bottom(18).width(130).height(26));
        FPSMLdlib2Theme.button(accept, FPSMLdlib2Theme.ButtonKind.PRIMARY);

        Button reject = new Button();
        reject.setId("fpsmatch.map_invitation.reject");
        reject.setText(Component.translatable("gui.fpsm.map_select.invitation.reject"));
        reject.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).right(24).bottom(18).width(130).height(26));
        FPSMLdlib2Theme.button(reject, FPSMLdlib2Theme.ButtonKind.DANGER);

        panel.addChildren(title, message, room, accept, reject);
        root.addChildren(system, panel);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))), accept, reject);
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private record Parts(ModularUI ui, Button accept, Button reject) {}
}
