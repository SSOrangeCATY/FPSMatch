package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.common.client.event.FPSMClientResetEvent;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapBootstrap;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapPacketLifecycle;
import net.minecraft.network.Connection;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class ForgeMinimapClientLifecycleEventSource
        implements MinimapPacketLifecycle.ClientEventSource,
        ClientMinimapBootstrap.EventSource {
    private final EventRegistrar events;
    private final Function<Connection, String> serverIdentity;

    ForgeMinimapClientLifecycleEventSource(IEventBus eventBus) {
        this(
                forgeEvents(Objects.requireNonNull(eventBus, "eventBus")),
                connection -> String.valueOf(connection.getRemoteAddress())
        );
    }

    ForgeMinimapClientLifecycleEventSource(
            EventRegistrar events,
            Function<Connection, String> serverIdentity
    ) {
        this.events = Objects.requireNonNull(events, "events");
        this.serverIdentity = Objects.requireNonNull(
                serverIdentity, "serverIdentity"
        );
    }

    @Override
    public void onLoggedIn(Consumer<Object> listener) {
        events.onLoggedIn(listener::accept);
    }

    @Override
    public void onLoggedOut(Consumer<Object> listener) {
        events.onLoggedOut(listener::accept);
    }

    @Override
    public void onReset(Runnable listener) {
        events.onReset(listener);
    }

    @Override
    public void bind(
            Consumer<String> onConnect,
            Runnable onDisconnect,
            Runnable onReset
    ) {
        Objects.requireNonNull(onConnect, "onConnect");
        Objects.requireNonNull(onDisconnect, "onDisconnect");
        Objects.requireNonNull(onReset, "onReset");
        events.onLoggedIn(connection ->
                onConnect.accept(serverIdentity.apply(connection)));
        events.onLoggedOut(ignored -> onDisconnect.run());
        events.onReset(onReset);
    }

    private static EventRegistrar forgeEvents(IEventBus eventBus) {
        return new EventRegistrar() {
            @Override
            public void onLoggedIn(Consumer<Connection> listener) {
                eventBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) ->
                        listener.accept(event.getConnection()));
            }

            @Override
            public void onLoggedOut(Consumer<Connection> listener) {
                eventBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                        listener.accept(event.getConnection()));
            }

            @Override
            public void onReset(Runnable listener) {
                eventBus.addListener((FPSMClientResetEvent event) -> listener.run());
            }
        };
    }

    interface EventRegistrar {
        void onLoggedIn(Consumer<Connection> listener);

        void onLoggedOut(Consumer<Connection> listener);

        void onReset(Runnable listener);
    }
}
