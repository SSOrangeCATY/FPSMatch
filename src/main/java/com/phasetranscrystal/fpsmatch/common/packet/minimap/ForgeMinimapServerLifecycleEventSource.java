package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeBootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.UUID;

public final class ForgeMinimapServerLifecycleEventSource
        implements MinimapPacketLifecycle.ServerEventSource,
        ServerMinimapRuntimeBootstrap.EventSource {
    private final EventRegistrar events;

    public ForgeMinimapServerLifecycleEventSource(IEventBus eventBus) {
        this(forgeEvents(Objects.requireNonNull(eventBus, "eventBus")));
    }

    ForgeMinimapServerLifecycleEventSource(EventRegistrar events) {
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public void onServerStarted(Consumer<Object> listener) {
        events.onServerStarted(listener);
    }

    @Override
    public void onConnectionOpened(BiConsumer<Object, Object> listener) {
        events.onConnectionOpened(listener);
    }

    @Override
    public void onConnectionClosed(Consumer<Object> listener) {
        events.onConnectionClosed(listener);
    }

    @Override
    public void onServerStopped(Consumer<Object> listener) {
        events.onServerStopped(listener);
    }

    @Override
    public void bind(
            Consumer<Object> onStart,
            Consumer<UUID> onLogout,
            Runnable onStop
    ) {
        events.onServerStarted(onStart);
        events.onPlayerLoggedOut(onLogout);
        events.onServerStopping(ignored -> onStop.run());
    }

    @Override
    public void bindTicks(LongConsumer onTick) {
        events.onServerTick(Objects.requireNonNull(onTick, "onTick"));
    }

    void bindBuiltinCatalog(
            Consumer<Object> onStart,
            Consumer<Object> onGlobalReload,
            Runnable onStopped
    ) {
        Objects.requireNonNull(onStart, "onStart");
        Objects.requireNonNull(onGlobalReload, "onGlobalReload");
        Objects.requireNonNull(onStopped, "onStopped");
        events.onServerStartedEarly(onStart);
        events.onDatapackSync((server, player) -> {
            if (player == null) {
                onGlobalReload.accept(server);
            }
        });
        events.onServerStopped(ignored -> onStopped.run());
    }

    private static EventRegistrar forgeEvents(IEventBus eventBus) {
        return new EventRegistrar() {
            @Override
            public void onServerStarted(Consumer<Object> listener) {
                eventBus.addListener(EventPriority.LOWEST, (ServerStartedEvent event) ->
                        listener.accept(event.getServer()));
            }

            @Override
            public void onServerStartedEarly(Consumer<Object> listener) {
                eventBus.addListener(EventPriority.HIGHEST, (ServerStartedEvent event) ->
                        listener.accept(event.getServer()));
            }

            @Override
            public void onDatapackSync(BiConsumer<Object, Object> listener) {
                eventBus.addListener((OnDatapackSyncEvent event) ->
                        listener.accept(
                                event.getPlayerList().getServer(),
                                event.getPlayer()
                        ));
            }

            @Override
            public void onConnectionOpened(BiConsumer<Object, Object> listener) {
                eventBus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        listener.accept(player.server, player.connection.connection);
                    }
                });
            }

            @Override
            public void onConnectionClosed(Consumer<Object> listener) {
                eventBus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        listener.accept(player.connection.connection);
                    }
                });
            }

            @Override
            public void onPlayerLoggedOut(Consumer<UUID> listener) {
                eventBus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        listener.accept(player.getUUID());
                    }
                });
            }

            @Override
            public void onServerStopping(Consumer<Object> listener) {
                eventBus.addListener(EventPriority.HIGHEST, (ServerStoppingEvent event) ->
                        listener.accept(event.getServer()));
            }

            @Override
            public void onServerStopped(Consumer<Object> listener) {
                eventBus.addListener((ServerStoppedEvent event) ->
                        listener.accept(event.getServer()));
            }

            @Override
            public void onServerTick(LongConsumer listener) {
                eventBus.addListener((TickEvent.ServerTickEvent event) -> {
                    if (event.phase == TickEvent.Phase.END) {
                        listener.accept(event.getServer().overworld().getGameTime());
                    }
                });
            }
        };
    }

    interface EventRegistrar {
        void onServerStarted(Consumer<Object> listener);

        void onServerStartedEarly(Consumer<Object> listener);

        void onDatapackSync(BiConsumer<Object, Object> listener);

        void onConnectionOpened(BiConsumer<Object, Object> listener);

        void onConnectionClosed(Consumer<Object> listener);

        void onPlayerLoggedOut(Consumer<UUID> listener);

        void onServerStopping(Consumer<Object> listener);

        void onServerStopped(Consumer<Object> listener);

        default void onServerTick(LongConsumer listener) {
        }
    }
}
