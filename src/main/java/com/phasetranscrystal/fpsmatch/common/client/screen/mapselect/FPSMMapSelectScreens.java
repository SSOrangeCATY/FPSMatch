package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class FPSMMapSelectScreens {
    private FPSMMapSelectScreens() {
    }

    public static void openSelection(MapSelectionSnapshotS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapSelectionScreen screen) {
            screen.applySnapshot(packet);
        } else {
            minecraft.setScreen(new FPSMMapSelectionScreen(packet, minecraft.screen));
        }
    }

    public static void openDetail(MapRoomDetailS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapDetailScreen screen) {
            screen.applyDetail(packet.detail());
        } else if (minecraft.screen instanceof FPSMMapDetailChildScreen screen) {
            screen.applyDetail(packet.detail());
        } else {
            minecraft.setScreen(new FPSMMapDetailScreen(packet.detail(), minecraft.screen));
        }
    }

    public static void openChild(Screen child) {
        Minecraft.getInstance().setScreen(child);
    }

    public static void openInvitation(MapRoomInvitationS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapInvitationScreen screen) {
            minecraft.setScreen(new FPSMMapInvitationScreen(packet, screen.parentScreen()));
        } else {
            minecraft.setScreen(new FPSMMapInvitationScreen(packet, minecraft.screen));
        }
    }
}
