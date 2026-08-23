package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;

import java.util.Objects;
import java.util.function.Consumer;

public final class ClientMinimapBootstrap {
    private final ClientMinimapServices services;
    private final Consumer<MinimapS2CDispatcher> dispatcherInstaller;
    private boolean installed;

    public ClientMinimapBootstrap(
            ClientMinimapServices services,
            Consumer<MinimapS2CDispatcher> dispatcherInstaller
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.dispatcherInstaller = Objects.requireNonNull(
                dispatcherInstaller,
                "dispatcherInstaller"
        );
    }

    public synchronized void install(EventSource events) {
        Objects.requireNonNull(events, "events");
        if (installed) {
            return;
        }
        installed = true;
        dispatcherInstaller.accept(services.dispatcher());
        events.bind(services::connect, services::disconnect, services::reset);
    }

    @FunctionalInterface
    public interface EventSource {
        void bind(
                Consumer<String> onConnect,
                Runnable onDisconnect,
                Runnable onReset
        );
    }
}
