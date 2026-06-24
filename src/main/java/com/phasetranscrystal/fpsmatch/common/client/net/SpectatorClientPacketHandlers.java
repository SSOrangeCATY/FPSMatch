package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorInspectPackets;
import com.phasetranscrystal.fpsmatch.compat.spectate.tacz.SpectatorGunInspectNet;

public final class SpectatorClientPacketHandlers {
    private SpectatorClientPacketHandlers() {
    }

    public static void handleWatchedPlayerInspect(SpectatorInspectPackets.S2CWatchedPlayerInspectPacket packet) {
        SpectatorGunInspectNet.handleWatchedPlayerInspectPacket(packet);
    }
}
