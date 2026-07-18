package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapC2SRequestHandler;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class FPSMatchMinimapGameTests {
    private static final String SERVER_RUNTIME_LIFECYCLE =
            "server_runtime_lifecycle";
    private static final String SUBSCRIPTION_CLEANUP =
            "subscription_cleanup";
    private static final String MAP_DIMENSION_RECONNECT =
            "map_dimension_reconnect";

    private FPSMatchMinimapGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath("fpsmatch", "minimap")
        );
        register(event, environment, SERVER_RUNTIME_LIFECYCLE);
        register(event, environment, SUBSCRIPTION_CLEANUP);
        register(event, environment, MAP_DIMENSION_RECONNECT);
    }

    private static void register(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment,
            String testId
    ) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("fpsmatch", "empty"),
                100,
                0,
                true,
                Rotation.NONE
        );
        event.registerTest(
                Identifier.fromNamespaceAndPath("fpsmatch", testId),
                new MinimapGameTestInstance(data, testId)
        );
    }

    public static void serverRuntimeLifecycle(GameTestHelper helper) {
        RecordingEvents events = new RecordingEvents();
        AtomicReference<MinimapC2SRequestHandler> handler =
                new AtomicReference<>();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger logouts = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        ServerMinimapRuntimeBootstrap bootstrap =
                new ServerMinimapRuntimeBootstrap(
                        handler::set,
                        server -> new ServerMinimapRuntimeBootstrap.ActiveRuntime() {
                            @Override
                            public void dispatch(
                                    UUID actorId,
                                    com.phasetranscrystal.fpsmatch.core.minimap.wire
                                            .MinimapWireMessage message
                            ) {
                                dispatches.incrementAndGet();
                            }

                            @Override
                            public void onPlayerLogout(UUID actorId) {
                                logouts.incrementAndGet();
                            }

                            @Override
                            public void tick(long nowTick) {
                                ticks.addAndGet(Math.toIntExact(nowTick));
                            }

                            @Override
                            public void close() {
                                closes.incrementAndGet();
                            }
                        }
                );
        bootstrap.install(events);
        UUID actorId = new UUID(0L, 1L);

        events.start.accept(helper.getLevel().getServer());
        boolean handled = handler.get().handle(actorId, subscribe());
        events.tick.accept(4L);
        events.logout.accept(actorId);
        events.stop.run();
        boolean rejectedAfterStop = !handler.get().handle(actorId, subscribe());

        if (!handled || !rejectedAfterStop
                || dispatches.get() != 1
                || ticks.get() != 4
                || logouts.get() != 1
                || closes.get() != 1) {
            helper.fail("minimap server runtime lifecycle did not clean up");
            return;
        }
        helper.succeed();
    }

    public static void subscriptionCleanup(GameTestHelper helper) {
        MapKey mapKey = new MapKey("test", "minimap");
        UUID playerId = new UUID(0L, 2L);
        MinimapSyncManager manager = new MinimapSyncManager(
                ignored -> true,
                new MinimapSyncQuotas(8, 8, 4)
        );
        NamespacedId documentId = NamespacedId.parse(
                "fpsmatch:gametest"
        );
        var runtimeHash = Sha256Digest.of(
                "gametest".getBytes(StandardCharsets.UTF_8)
        );
        boolean hud = manager.subscribe(
                playerId, WireIdentity.Scope.MATCH_HUD, mapKey,
                documentId, 1L, runtimeHash
        );
        boolean tactical = manager.subscribe(
                playerId, WireIdentity.Scope.TACTICAL_SCREEN, mapKey,
                documentId, 1L, runtimeHash
        );
        manager.onPlayerLogout(playerId);

        if (!hud || !tactical || manager.subscriptionCount(playerId) != 0) {
            helper.fail("player logout retained minimap subscriptions");
            return;
        }
        helper.succeed();
    }

    public static void mapDimensionSwitchAndReconnectCleanup(
            GameTestHelper helper
    ) {
        UUID playerId = new UUID(0L, 4L);
        WireIdentity.MapTarget overworld = target(
                "alpha", "minecraft:overworld"
        );
        WireIdentity.MapTarget nether = target(
                "bravo", "minecraft:the_nether"
        );
        TestRuntimeSource overworldSource = source(overworld, "alpha");
        TestRuntimeSource netherSource = source(nether, "bravo");
        ArrayList<WireIdentity.MapTarget> resolved = new ArrayList<>();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> {
                    resolved.add(target);
                    if (!actorId.equals(playerId)) {
                        return Optional.empty();
                    }
                    return Optional.of(target.equals(overworld)
                            ? overworldSource
                            : netherSource);
                },
                (actorId, target) -> Optional.empty(),
                (actorId, message) -> sent.add(message),
                UUID::randomUUID,
                () -> 20
        );
        WireIdentity.ScopeLease first = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 1L, 1L
        );
        WireIdentity.ScopeLease switched = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 2L, 2L
        );
        WireIdentity.ScopeLease reconnected = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 3L, 3L
        );

        router.dispatch(playerId, subscribe(first, overworld, 5L));
        router.dispatch(playerId, subscribe(switched, nether, 6L));
        sent.clear();
        resolved.clear();
        router.tick(0L);
        boolean switchedOnly = resolved.equals(List.of(nether));

        router.onPlayerLogout(playerId);
        resolved.clear();
        router.tick(1L);
        boolean logoutClean = resolved.isEmpty();

        router.dispatch(playerId, subscribe(reconnected, overworld, 7L));
        long acknowledgements = sent.stream()
                .filter(RuntimeWireMessage.ScopeAck.class::isInstance)
                .count();

        if (!switchedOnly || !logoutClean || acknowledgements != 1L) {
            helper.fail("map/dimension switch or reconnect retained stale subscriptions");
            return;
        }
        helper.succeed();
    }

    private static final class MinimapGameTestInstance
            extends GameTestInstance {
        private static final MapCodec<MinimapGameTestInstance> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TestData.CODEC.fieldOf("data")
                                .forGetter(MinimapGameTestInstance::info),
                        Codec.STRING.fieldOf("testId")
                                .forGetter(MinimapGameTestInstance::testId)
                ).apply(instance, MinimapGameTestInstance::new));

        private final String testId;

        private MinimapGameTestInstance(
                TestData<Holder<TestEnvironmentDefinition<?>>> data,
                String testId
        ) {
            super(data);
            this.testId = testId;
        }

        @Override
        public void run(GameTestHelper helper) {
            switch (testId) {
                case SERVER_RUNTIME_LIFECYCLE -> serverRuntimeLifecycle(helper);
                case SUBSCRIPTION_CLEANUP -> subscriptionCleanup(helper);
                case MAP_DIMENSION_RECONNECT ->
                        mapDimensionSwitchAndReconnectCleanup(helper);
                default -> throw new IllegalStateException(
                        "Unknown FPSMatch minimap GameTest: " + testId
                );
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("FPSMatch minimap");
        }

        private String testId() {
            return testId;
        }
    }

    private static RuntimeWireMessage.Subscribe subscribe() {
        return new RuntimeWireMessage.Subscribe(
                new UUID(0L, 3L),
                new WireIdentity.ScopeLease(
                        WireIdentity.Scope.MATCH_HUD, 1L, 1L
                ),
                new WireIdentity.MapTarget(
                        new MapKey("test", "minimap"),
                        NamespacedId.parse("minecraft:overworld")
                ),
                Optional.empty()
        );
    }

    private static RuntimeWireMessage.Subscribe subscribe(
            WireIdentity.ScopeLease lease,
            WireIdentity.MapTarget target,
            long requestId
    ) {
        return new RuntimeWireMessage.Subscribe(
                new UUID(0L, requestId), lease, target, Optional.empty()
        );
    }

    private static WireIdentity.MapTarget target(
            String mapName,
            String dimension
    ) {
        return new WireIdentity.MapTarget(
                new MapKey("test", mapName), NamespacedId.parse(dimension)
        );
    }

    private static TestRuntimeSource source(
            WireIdentity.MapTarget target,
            String payload
    ) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return new TestRuntimeSource(new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        target, NamespacedId.parse("fpsmatch:" + payload)
                ),
                1L,
                Sha256Digest.of(bytes),
                Optional.empty()
        ), bytes);
    }

    private static final class RecordingEvents
            implements ServerMinimapRuntimeBootstrap.EventSource {
        private Consumer<Object> start;
        private Consumer<UUID> logout;
        private Runnable stop;
        private LongConsumer tick;

        @Override
        public void bind(
                Consumer<Object> onStart,
                Consumer<UUID> onLogout,
                Runnable onStop
        ) {
            start = onStart;
            logout = onLogout;
            stop = onStop;
        }

        @Override
        public void bindTicks(LongConsumer onTick) {
            tick = onTick;
        }
    }

    private record TestRuntimeSource(
            WireIdentity.RuntimeIdentity identity,
            byte[] manifestBytes
    ) implements RuntimeMapSource {
        private TestRuntimeSource {
            manifestBytes = manifestBytes.clone();
        }

        @Override
        public byte[] manifestBytes() {
            return manifestBytes.clone();
        }

        @Override
        public Optional<RuntimeEntryDescriptor> descriptor(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath path
        ) {
            return Optional.empty();
        }

        @Override
        public InputStream openEntry(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath path
        ) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void close() {
        }
    }
}
