package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 屏幕导航跳转工具类
 */
public class FPSMMapSelectScreens {
    public static void openSelection(MapSelectionSnapshotS2CPacket packet) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof FPSMMapSelectionScreen sel) {
            sel.applySnapshot(packet);
        } else {
            Minecraft.getInstance().setScreen(new FPSMMapSelectionScreen(packet, current));
        }
    }

    public static void openDetail(MapRoomDetailS2CPacket packet) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof FPSMMapDetailChildScreen child) {
            child.applyDetail(packet.detail());
        } else {
            Minecraft.getInstance().setScreen(new FPSMMapDetailScreen(packet.detail(),
                    current instanceof FPSMMapSelectionScreen ? current : null));
        }
    }

    public static void openInvitation(MapRoomInvitationS2CPacket packet) {
        Minecraft.getInstance().setScreen(new FPSMMapInvitationScreen(packet));
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static void openChild(FPSMMapDetailChildScreen child) {
        Minecraft.getInstance().setScreen((Screen) child);
    }
}