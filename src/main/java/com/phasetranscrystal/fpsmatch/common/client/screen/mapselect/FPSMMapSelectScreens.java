package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2.Ldlib2MapSelectionScreen;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Map-room UI router.
 * Product open path always uses LDLib2 ModularUIScreen via {@link Ldlib2MapSelectionScreen}.
 */
public final class FPSMMapSelectScreens {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FPSMMapSelectScreens() {
    }

    public static boolean isMapSelectionScreen(Screen screen) {
        return screen instanceof Ldlib2MapSelectionScreen;
    }

    /**
     * Active open/refresh: apply into an already-open LDLib2 browser, otherwise open a new one.
     */
    public static void openSelection(MapSelectionSnapshotS2CPacket packet) {
        openSelection(packet, sanitizeParent(Minecraft.getInstance().screen));
    }

    /**
     * Active open with an explicit parent. Pass {@code null} from the ESC pause entry point so
     * closing the browser does not re-open {@link PauseScreen} (which freezes the integrated server).
     */
    public static void openSelection(MapSelectionSnapshotS2CPacket packet, @Nullable Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof Ldlib2MapSelectionScreen screen) {
            screen.applySnapshot(packet);
            return;
        }
        try {
            minecraft.setScreen(new Ldlib2MapSelectionScreen(packet, parent));
            if (!(minecraft.screen instanceof Ldlib2MapSelectionScreen)) {
                throw new IllegalStateException("LDLib2 map selection screen was not installed");
            }
        } catch (Throwable error) {
            // Fail hard: do not fall back to classic widget screens.
            LOGGER.error("Failed to open LDLib2 map selection UI", error);
        }
    }

    public static void openDetail(MapRoomDetailS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapDetailChildScreen screen) {
            screen.applyDetail(packet.detail());
            return;
        }
        openSelection(new MapSelectionSnapshotS2CPacket(
                java.util.List.of(packet.detail().summary()),
                packet.detail().summary().currentPlayerOp(),
                true));
        if (minecraft.screen instanceof FPSMMapDetailChildScreen screen) {
            screen.applyDetail(packet.detail());
        }
    }

    /**
     * Passive detail update: only refresh when a detail/child screen is already open.
     */
    public static void applyDetailIfOpen(com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail detail) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FPSMMapDetailChildScreen screen) {
            screen.applyDetail(detail);
        }
    }

    /**
     * Passive list update: only refresh when the list screen is already open.
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

    @Nullable
    private static Screen sanitizeParent(@Nullable Screen screen) {
        if (screen instanceof PauseScreen || isMapSelectionScreen(screen)) {
            return null;
        }
        return screen;
    }
}
