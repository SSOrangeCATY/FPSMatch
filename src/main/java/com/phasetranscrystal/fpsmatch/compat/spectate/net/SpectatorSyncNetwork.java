package com.phasetranscrystal.fpsmatch.compat.spectate.net;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers spectator sync packets (TACZ) on the main FPSMatch payload channel.
 */
public final class SpectatorSyncNetwork {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private SpectatorSyncNetwork() {
    }

    public static void registerPackets() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        SpectatorInspectPackets.register();
    }
}
