package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Common-safe lifecycle entry point used by Forge side adapters. */
public final class MinimapPacketLifecycle {
    private MinimapPacketLifecycle() {
    }

    public static void bindServer(ServerEventSource source) {
        MinimapPacketEndpointRuntime.LIFECYCLE.bindServer(
                Objects.requireNonNull(source, "source")
        );
    }

    public static void bindClient(ClientEventSource source) {
        MinimapPacketEndpointRuntime.LIFECYCLE.bindClient(
                Objects.requireNonNull(source, "source")
        );
    }

    public interface ServerEventSource {
        void onServerStarted(Consumer<Object> listener);

        void onConnectionOpened(BiConsumer<Object, Object> listener);

        void onConnectionClosed(Consumer<Object> listener);

        void onServerStopped(Consumer<Object> listener);
    }

    public interface ClientEventSource {
        void onLoggedIn(Consumer<Object> listener);

        void onLoggedOut(Consumer<Object> listener);

        void onReset(Runnable listener);
    }
}
