package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2.Ldlib2MapSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class FPSMMapSelectScreens {
    private FPSMMapSelectScreens() {
    }

    public static void openSelection(MapSelectionSnapshotS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof Ldlib2MapSelectionScreen screen) {
            screen.applySnapshot(packet);
        } else {
            minecraft.setScreen(new Ldlib2MapSelectionScreen(packet, minecraft.screen));
        }
    }

    public static void openDetail(MapRoomDetailS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapDetailChildScreen screen) {
            screen.applyDetail(packet.detail());
        } else {
            minecraft.setScreen(new Ldlib2MapSelectionScreen(
                    new MapSelectionSnapshotS2CPacket(java.util.List.of(packet.detail().summary()),
                            packet.detail().summary().currentPlayerOp(), true),
                    minecraft.screen));
            if (minecraft.screen instanceof Ldlib2MapSelectionScreen screen) {
                screen.applyDetail(packet.detail());
            }
        }
    }

    /**
     * 被动详情更新：仅当当前已打开详情/子界面时原地刷新，绝不强制打开界面。
     * 用于订阅式广播，避免对局内玩家被弹出 GUI。
     */
    public static void applyDetailIfOpen(com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail detail) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapDetailChildScreen screen) {
            screen.applyDetail(detail);
        }
    }

    /**
     * 被动列表更新：仅当当前已打开列表界面时原地刷新，绝不强制打开界面。
     */
    public static void applySelectionIfOpen(MapSelectionSnapshotS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof Ldlib2MapSelectionScreen screen) {
            screen.applySnapshot(packet);
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
