package com.phasetranscrystal.fpsmatch.core.capability;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityMapLifecycleTest {
    private static final AtomicInteger INITIALIZATIONS = new AtomicInteger();
    private static final AtomicInteger DESTROYS = new AtomicInteger();
    private static final AtomicInteger REGISTRATION_FAILURE_INITIALIZATIONS = new AtomicInteger();
    private static final AtomicInteger REGISTRATION_FAILURE_DESTROYS = new AtomicInteger();
    private static final AtomicInteger PROBE_EVENTS = new AtomicInteger();
    private static final AtomicInteger BLOCKING_INITIALIZATIONS = new AtomicInteger();
    private static final AtomicInteger BLOCKING_DESTROYS = new AtomicInteger();
    private static final AtomicReference<CyclicBarrier> FACTORY_BARRIER = new AtomicReference<>();
    private static CountDownLatch blockingInitEntered;
    private static CountDownLatch releaseBlockingInit;
    private static final AtomicReference<CapabilityMap<BaseMap, MapCapability>> REENTRANT_MAP =
            new AtomicReference<>();
    private static final AtomicReference<CapabilityMap<BaseMap, MapCapability>> CROSS_TYPE_MAP =
            new AtomicReference<>();
    private static CountDownLatch crossTypeFactoriesReady;
    private static final AtomicInteger PERSISTENT_FACTORY_CALLS = new AtomicInteger();
    private static CountDownLatch persistentFactoryEntered;
    private static CountDownLatch releasePersistentFactory;
    private static CountDownLatch lifecycleInitEntered;
    private static CountDownLatch releaseLifecycleInit;
    private static CountDownLatch lifecycleDecodeEntered;
    private static CountDownLatch releaseLifecycleDecode;
    private static final AtomicInteger DECODE_AFTER_DESTROY = new AtomicInteger();
    private static CountDownLatch lifecycleSerializeEntered;
    private static CountDownLatch releaseLifecycleSerialize;
    private static final AtomicInteger SERIALIZE_AFTER_DESTROY = new AtomicInteger();

    @BeforeAll
    static void registerFixtures() {
        if (!FPSMCapabilityManager.isRegistered(ConcurrentLifecycleCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    ConcurrentLifecycleCapability.class,
                    new FPSMCapability.Factory<BaseMap, ConcurrentLifecycleCapability>() {
                        @Override
                        public ConcurrentLifecycleCapability create(BaseMap map) {
                            ConcurrentLifecycleCapability capability =
                                    new ConcurrentLifecycleCapability(map);
                            awaitBarrier(FACTORY_BARRIER.get());
                            return capability;
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(InitFailingCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    InitFailingCapability.class,
                    new FPSMCapability.Factory<BaseMap, InitFailingCapability>() {
                        @Override
                        public InitFailingCapability create(BaseMap map) {
                            return new InitFailingCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(RegistrationFailingCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    RegistrationFailingCapability.class,
                    new FPSMCapability.Factory<BaseMap, RegistrationFailingCapability>() {
                        @Override
                        public RegistrationFailingCapability create(BaseMap map) {
                            return new RegistrationFailingCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(EventVisibilityCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    EventVisibilityCapability.class,
                    EventVisibilityCapability::new
            );
        }
        if (!FPSMCapabilityManager.isRegistered(CrossTypeCycleACapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    CrossTypeCycleACapability.class,
                    new FPSMCapability.Factory<BaseMap, CrossTypeCycleACapability>() {
                        @Override
                        public CrossTypeCycleACapability create(BaseMap map) {
                            awaitCrossTypeFactoryPeer();
                            return new CrossTypeCycleACapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(CrossTypeCycleBCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    CrossTypeCycleBCapability.class,
                    new FPSMCapability.Factory<BaseMap, CrossTypeCycleBCapability>() {
                        @Override
                        public CrossTypeCycleBCapability create(BaseMap map) {
                            awaitCrossTypeFactoryPeer();
                            return new CrossTypeCycleBCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(CrossTypeParentCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    CrossTypeParentCapability.class,
                    CrossTypeParentCapability::new
            );
        }
        if (!FPSMCapabilityManager.isRegistered(CrossTypeDependencyCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    CrossTypeDependencyCapability.class,
                    CrossTypeDependencyCapability::new
            );
        }
        if (!FPSMCapabilityManager.isRegistered(BlockingInitCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    BlockingInitCapability.class,
                    new FPSMCapability.Factory<BaseMap, BlockingInitCapability>() {
                        @Override
                        public BlockingInitCapability create(BaseMap map) {
                            return new BlockingInitCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(ReentrantFactoryCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    ReentrantFactoryCapability.class,
                    new FPSMCapability.Factory<BaseMap, ReentrantFactoryCapability>() {
                        @Override
                        public ReentrantFactoryCapability create(BaseMap map) {
                            REENTRANT_MAP.get().getOrCreate(ReentrantFactoryCapability.class);
                            return new ReentrantFactoryCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(ReentrantInitCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    ReentrantInitCapability.class,
                    new FPSMCapability.Factory<BaseMap, ReentrantInitCapability>() {
                        @Override
                        public ReentrantInitCapability create(BaseMap map) {
                            return new ReentrantInitCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(PersistentClaimCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    PersistentClaimCapability.class,
                    new FPSMCapability.Factory<BaseMap, PersistentClaimCapability>() {
                        @Override
                        public PersistentClaimCapability create(BaseMap map) {
                            if (PERSISTENT_FACTORY_CALLS.incrementAndGet() == 1) {
                                persistentFactoryEntered.countDown();
                                awaitLatch(releasePersistentFactory, "persistent factory");
                            }
                            return new PersistentClaimCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(DestroyFailingCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    DestroyFailingCapability.class,
                    new FPSMCapability.Factory<BaseMap, DestroyFailingCapability>() {
                        @Override
                        public DestroyFailingCapability create(BaseMap map) {
                            return new DestroyFailingCapability(map);
                        }
                    }
            );
        }
        if (!FPSMCapabilityManager.isRegistered(LifecycleAwareSavableCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    LifecycleAwareSavableCapability.class,
                    new FPSMCapability.Factory<BaseMap, LifecycleAwareSavableCapability>() {
                        @Override
                        public LifecycleAwareSavableCapability create(BaseMap map) {
                            return new LifecycleAwareSavableCapability(map);
                        }
                    }
            );
        }
    }

    @BeforeEach
    void resetCounters() {
        INITIALIZATIONS.set(0);
        DESTROYS.set(0);
        REGISTRATION_FAILURE_INITIALIZATIONS.set(0);
        REGISTRATION_FAILURE_DESTROYS.set(0);
        PROBE_EVENTS.set(0);
        BLOCKING_INITIALIZATIONS.set(0);
        BLOCKING_DESTROYS.set(0);
        FACTORY_BARRIER.set(null);
        blockingInitEntered = new CountDownLatch(1);
        releaseBlockingInit = new CountDownLatch(1);
        REENTRANT_MAP.set(null);
        CROSS_TYPE_MAP.set(null);
        crossTypeFactoriesReady = new CountDownLatch(2);
        PERSISTENT_FACTORY_CALLS.set(0);
        persistentFactoryEntered = new CountDownLatch(1);
        releasePersistentFactory = new CountDownLatch(1);
        lifecycleInitEntered = new CountDownLatch(1);
        releaseLifecycleInit = new CountDownLatch(1);
        lifecycleDecodeEntered = new CountDownLatch(1);
        releaseLifecycleDecode = new CountDownLatch(1);
        DECODE_AFTER_DESTROY.set(0);
        lifecycleSerializeEntered = new CountDownLatch(1);
        releaseLifecycleSerialize = new CountDownLatch(1);
        SERIALIZE_AFTER_DESTROY.set(0);
    }

    @Test
    void concurrentAddRegistersAndInitializesOneInstance() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        CyclicBarrier containsBarrier = new CyclicBarrier(2);
        replaceStorage(
                capabilities,
                new CoordinatedStorage(containsBarrier, ConcurrentLifecycleCapability.class)
        );
        FACTORY_BARRIER.set(new CyclicBarrier(1));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> capabilities.add(ConcurrentLifecycleCapability.class)
            );
            Future<Boolean> second = executor.submit(
                    () -> capabilities.add(ConcurrentLifecycleCapability.class)
            );
            List<Boolean> outcomes = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );

            assertAll(
                    () -> assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count()),
                    () -> assertEquals(1, INITIALIZATIONS.get()),
                    () -> assertEquals(1, capabilities.stream()
                            .filter(ConcurrentLifecycleCapability.class::isInstance)
                            .count())
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publishedLookupAndRemoveAreSerialized() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(ConcurrentLifecycleCapability.class));
        ConcurrentLifecycleCapability published = capabilities
                .get(ConcurrentLifecycleCapability.class)
                .orElseThrow();
        BlockingPublishedLookupStorage storage =
                new BlockingPublishedLookupStorage(ConcurrentLifecycleCapability.class);
        storage.put(ConcurrentLifecycleCapability.class, published);
        replaceStorage(capabilities, storage);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            storage.arm();
            Future<Boolean> duplicate = executor.submit(
                    () -> capabilities.add(ConcurrentLifecycleCapability.class)
            );
            assertTrue(storage.lookupEntered.await(5, TimeUnit.SECONDS));
            Future<Boolean> removal = executor.submit(
                    () -> capabilities.remove(ConcurrentLifecycleCapability.class)
            );

            assertThrows(
                    TimeoutException.class,
                    () -> removal.get(200, TimeUnit.MILLISECONDS)
            );
            storage.release();

            assertFalse(duplicate.get(5, TimeUnit.SECONDS));
            assertTrue(removal.get(5, TimeUnit.SECONDS));
            assertFalse(capabilities.contains(ConcurrentLifecycleCapability.class));
        } finally {
            storage.release();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
            capabilities.remove(ConcurrentLifecycleCapability.class);
        }
    }

    @Test
    void initializationFailureRollsBackMapAndDestroysPartialState() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        assertThrows(
                IllegalStateException.class,
                () -> capabilities.add(InitFailingCapability.class)
        );
        assertAll(
                () -> assertFalse(capabilities.get(InitFailingCapability.class).isPresent()),
                () -> assertEquals(1, DESTROYS.get())
        );
    }

    @Test
    void registrationFailureAfterInitializationDestroysCandidateAndRollsBackPublication() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        assertThrows(
                RuntimeException.class,
                () -> capabilities.add(RegistrationFailingCapability.class)
        );

        assertAll(
                () -> assertFalse(capabilities.get(RegistrationFailingCapability.class).isPresent()),
                () -> assertEquals(1, REGISTRATION_FAILURE_INITIALIZATIONS.get()),
                () -> assertEquals(1, REGISTRATION_FAILURE_DESTROYS.get())
        );
    }

    @Test
    void eventSubscriberBecomesVisibleOnlyAfterInitializationCompletes() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        MinecraftForge.EVENT_BUS.start();
        try {
            assertTrue(capabilities.add(EventVisibilityCapability.class));
            int eventsSeenDuringInitialization = PROBE_EVENTS.get();

            MinecraftForge.EVENT_BUS.post(new ProbeEvent());

            assertAll(
                    () -> assertEquals(0, eventsSeenDuringInitialization),
                    () -> assertEquals(1, PROBE_EVENTS.get())
            );
        } finally {
            try {
                capabilities.remove(EventVisibilityCapability.class);
            } finally {
                MinecraftForge.EVENT_BUS.shutdown();
            }
        }
    }

    @Test
    void crossTypeInitializationCycleFailsFastAndRollsBackBothCapabilities()
            throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        CROSS_TYPE_MAP.set(capabilities);
        CountDownLatch additionsCompleted = new CountDownLatch(2);
        ExecutorService executor = newDaemonExecutor(2, "capability-cycle-test");
        try {
            Future<Boolean> first = executor.submit(() -> {
                try {
                    return capabilities.add(CrossTypeCycleACapability.class);
                } finally {
                    additionsCompleted.countDown();
                }
            });
            Future<Boolean> second = executor.submit(() -> {
                try {
                    return capabilities.add(CrossTypeCycleBCapability.class);
                } finally {
                    additionsCompleted.countDown();
                }
            });

            assertTrue(
                    additionsCompleted.await(1, TimeUnit.SECONDS),
                    "cross-type initialization cycle must not deadlock"
            );
            assertFastIllegalState(first);
            assertFastIllegalState(second);
            assertAll(
                    () -> assertFalse(capabilities.contains(CrossTypeCycleACapability.class)),
                    () -> assertFalse(capabilities.contains(CrossTypeCycleBCapability.class))
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
            CROSS_TYPE_MAP.set(null);
        }
    }

    @Test
    void acyclicCrossTypeInitializationPublishesDependencyBeforeParent() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        CROSS_TYPE_MAP.set(capabilities);
        try {
            assertTrue(capabilities.add(CrossTypeParentCapability.class));

            assertAll(
                    () -> assertTrue(capabilities.contains(CrossTypeDependencyCapability.class)),
                    () -> assertTrue(capabilities.contains(CrossTypeParentCapability.class))
            );
        } finally {
            capabilities.remove(CrossTypeParentCapability.class);
            capabilities.remove(CrossTypeDependencyCapability.class);
            CROSS_TYPE_MAP.set(null);
        }
    }

    @Test
    void getDoesNotExposeCapabilityWhileInitializationIsInFlight() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> addition = executor.submit(
                    () -> capabilities.add(BlockingInitCapability.class)
            );
            assertTrue(blockingInitEntered.await(5, TimeUnit.SECONDS));

            assertTrue(capabilities.get(BlockingInitCapability.class).isEmpty());

            releaseBlockingInit.countDown();
            assertTrue(addition.get(5, TimeUnit.SECONDS));
            BlockingInitCapability published = capabilities
                    .get(BlockingInitCapability.class)
                    .orElseThrow();
            assertTrue(published.initialized);
        } finally {
            releaseBlockingInit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void getOrCreateWaitsForInFlightAddAndReturnsPublishedInstance() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> addition = executor.submit(
                    () -> capabilities.add(BlockingInitCapability.class)
            );
            assertTrue(blockingInitEntered.await(5, TimeUnit.SECONDS));
            Future<Optional<BlockingInitCapability>> lookup = executor.submit(
                    () -> capabilities.getOrCreate(BlockingInitCapability.class)
            );

            assertThrows(
                    TimeoutException.class,
                    () -> lookup.get(200, TimeUnit.MILLISECONDS)
            );
            releaseBlockingInit.countDown();

            assertTrue(addition.get(5, TimeUnit.SECONDS));
            BlockingInitCapability returned = lookup.get(5, TimeUnit.SECONDS).orElseThrow();
            assertTrue(returned.initialized);
            assertSame(returned, capabilities.get(BlockingInitCapability.class).orElseThrow());
            assertEquals(1, BLOCKING_INITIALIZATIONS.get());
        } finally {
            releaseBlockingInit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void removeDuringInitializationCancelsPendingAddWithoutResurrection() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> addition = executor.submit(
                    () -> capabilities.add(BlockingInitCapability.class)
            );
            assertTrue(blockingInitEntered.await(5, TimeUnit.SECONDS));

            assertTrue(capabilities.remove(BlockingInitCapability.class));
            releaseBlockingInit.countDown();

            assertFalse(addition.get(5, TimeUnit.SECONDS));
            assertTrue(capabilities.get(BlockingInitCapability.class).isEmpty());
            assertEquals(1, BLOCKING_DESTROYS.get());
        } finally {
            releaseBlockingInit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sameTypeFactoryReentryFailsFastAndDoesNotLeakPendingClaim() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        REENTRANT_MAP.set(capabilities);
        ExecutorService executor = newDaemonExecutor();
        try {
            assertFastIllegalState(
                    executor.submit(() -> capabilities.add(ReentrantFactoryCapability.class))
            );
            assertFastIllegalState(
                    executor.submit(() -> capabilities.add(ReentrantFactoryCapability.class))
            );
            assertTrue(capabilities.get(ReentrantFactoryCapability.class).isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameTypeInitReentryFailsFastAndDoesNotLeakPendingClaim() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        REENTRANT_MAP.set(capabilities);
        ExecutorService executor = newDaemonExecutor();
        try {
            assertFastIllegalState(
                    executor.submit(() -> capabilities.add(ReentrantInitCapability.class))
            );
            assertFastIllegalState(
                    executor.submit(() -> capabilities.add(ReentrantInitCapability.class))
            );
            assertTrue(capabilities.get(ReentrantInitCapability.class).isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void persistenceClaimsBeforeConstructionAndPublishesDecodedState() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> restore = executor.submit(() -> capabilities.write(Map.of(
                    PersistentClaimCapability.class.getSimpleName(),
                    new JsonPrimitive("persisted")
            )));
            assertTrue(persistentFactoryEntered.await(5, TimeUnit.SECONDS));
            Future<Optional<PersistentClaimCapability>> lookup = executor.submit(
                    () -> capabilities.getOrCreate(PersistentClaimCapability.class)
            );

            assertThrows(
                    TimeoutException.class,
                    () -> lookup.get(200, TimeUnit.MILLISECONDS)
            );
            releasePersistentFactory.countDown();

            restore.get(5, TimeUnit.SECONDS);
            PersistentClaimCapability returned = lookup.get(5, TimeUnit.SECONDS).orElseThrow();
            assertAll(
                    () -> assertEquals("persisted", returned.read()),
                    () -> assertEquals("persisted", returned.valueSeenDuringInit),
                    () -> assertSame(
                            returned,
                            capabilities.get(PersistentClaimCapability.class).orElseThrow()
                    ),
                    () -> assertEquals(1, PERSISTENT_FACTORY_CALLS.get())
            );
        } finally {
            releasePersistentFactory.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedPendingWaitFailsFastRestoresFlagAndDoesNotCancelOwner() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Thread> waiterThread = new AtomicReference<>();
        try {
            Future<Boolean> owner = executor.submit(
                    () -> capabilities.add(BlockingInitCapability.class)
            );
            assertTrue(blockingInitEntered.await(5, TimeUnit.SECONDS));
            Future<InterruptedWaitResult> waiter = executor.submit(() -> {
                waiterThread.set(Thread.currentThread());
                try {
                    capabilities.getOrCreate(BlockingInitCapability.class);
                    return new InterruptedWaitResult(null, Thread.currentThread().isInterrupted());
                } catch (Throwable failure) {
                    return new InterruptedWaitResult(
                            failure,
                            Thread.currentThread().isInterrupted()
                    );
                }
            });
            awaitWaiting(waiterThread);

            waiterThread.get().interrupt();
            InterruptedWaitResult result = waiter.get(1, TimeUnit.SECONDS);

            assertInstanceOf(IllegalStateException.class, result.failure());
            assertTrue(result.interrupted());
            assertFalse(owner.isDone());

            releaseBlockingInit.countDown();
            assertTrue(owner.get(5, TimeUnit.SECONDS));
            assertTrue(capabilities.get(BlockingInitCapability.class).isPresent());
        } finally {
            releaseBlockingInit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void removeUnregistersPublishedCapabilityWhenDestroyThrows() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(DestroyFailingCapability.class));
        DestroyFailingCapability capability = capabilities
                .get(DestroyFailingCapability.class)
                .orElseThrow();
        Map<Object, List<?>> listeners = eventBusListeners();
        listeners.put(capability, new ArrayList<>());
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> capabilities.remove(DestroyFailingCapability.class)
            );
            assertEquals("fixture destroy failed", failure.getMessage());
            assertEquals(0, failure.getSuppressed().length);
            assertFalse(listeners.containsKey(capability));
            assertTrue(capabilities.get(DestroyFailingCapability.class).isEmpty());
        } finally {
            capability.failHashCode = false;
            listeners.remove(capability);
        }
    }

    @Test
    void removeKeepsDestroyAsPrimaryAndSuppressesUnregisterFailure() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(DestroyFailingCapability.class));
        DestroyFailingCapability capability = capabilities
                .get(DestroyFailingCapability.class)
                .orElseThrow();
        capability.failUnregister = true;
        Map<Object, List<?>> listeners = eventBusListeners();
        listeners.put(capability, new ArrayList<>());
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> capabilities.remove(DestroyFailingCapability.class)
            );
            assertEquals("fixture destroy failed", failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertEquals(
                    "fixture unregister failed",
                    failure.getSuppressed()[0].getMessage()
            );
            assertTrue(capabilities.get(DestroyFailingCapability.class).isEmpty());
        } finally {
            capability.failHashCode = false;
            listeners.remove(capability);
        }
    }

    @Test
    void pendingRestoreDecodeAndRemoveAreAtomicForTheCapabilityType() throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        AtomicReference<Thread> restoreThread = new AtomicReference<>();
        try {
            Future<Boolean> owner = executor.submit(
                    () -> capabilities.add(LifecycleAwareSavableCapability.class)
            );
            assertTrue(lifecycleInitEntered.await(5, TimeUnit.SECONDS));
            Future<?> restore = executor.submit(() -> {
                restoreThread.set(Thread.currentThread());
                capabilities.write(Map.of(
                        LifecycleAwareSavableCapability.class.getSimpleName(),
                        new JsonPrimitive("restored")
                ));
            });
            awaitWaiting(restoreThread);

            releaseLifecycleInit.countDown();
            assertTrue(owner.get(5, TimeUnit.SECONDS));
            assertTrue(lifecycleDecodeEntered.await(5, TimeUnit.SECONDS));

            Future<Boolean> removal = executor.submit(
                    () -> capabilities.remove(LifecycleAwareSavableCapability.class)
            );
            AtomicBoolean removalWaitedForDecode = new AtomicBoolean();
            try {
                removal.get(200, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                removalWaitedForDecode.set(true);
            }

            releaseLifecycleDecode.countDown();
            restore.get(5, TimeUnit.SECONDS);
            assertTrue(removal.get(5, TimeUnit.SECONDS));

            assertAll(
                    () -> assertTrue(removalWaitedForDecode.get()),
                    () -> assertEquals(0, DECODE_AFTER_DESTROY.get()),
                    () -> assertTrue(capabilities
                            .get(LifecycleAwareSavableCapability.class)
                            .isEmpty())
            );
        } finally {
            releaseLifecycleInit.countDown();
            releaseLifecycleDecode.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void persistenceSnapshotAndRemoveAreAtomicForTheCapabilityLifecycle()
            throws Exception {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        releaseLifecycleInit.countDown();
        releaseLifecycleDecode.countDown();
        assertTrue(capabilities.add(LifecycleAwareSavableCapability.class));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CapabilityMap.Wrapper> snapshot = executor.submit(capabilities::getData);
            assertTrue(lifecycleSerializeEntered.await(5, TimeUnit.SECONDS));
            Future<Boolean> removal = executor.submit(
                    () -> capabilities.remove(LifecycleAwareSavableCapability.class)
            );

            assertThrows(
                    TimeoutException.class,
                    () -> removal.get(200, TimeUnit.MILLISECONDS)
            );
            releaseLifecycleSerialize.countDown();

            snapshot.get(5, TimeUnit.SECONDS);
            assertTrue(removal.get(5, TimeUnit.SECONDS));
            assertEquals(0, SERIALIZE_AFTER_DESTROY.get());
        } finally {
            releaseLifecycleSerialize.countDown();
            executor.shutdownNow();
        }
    }

    private static void replaceStorage(
            CapabilityMap<BaseMap, MapCapability> capabilities,
            ConcurrentHashMap<Class<? extends MapCapability>, MapCapability> storage
    ) throws ReflectiveOperationException {
        Field field = CapabilityMap.class.getDeclaredField("capabilities");
        field.setAccessible(true);
        field.set(capabilities, storage);
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        if (barrier == null) {
            return;
        }
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("capability test barrier failed", failure);
        }
    }

    private static void awaitLatch(CountDownLatch latch, String name) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(name + " timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(name + " interrupted", interrupted);
        }
    }

    private static void awaitCrossTypeFactoryPeer() {
        crossTypeFactoriesReady.countDown();
        try {
            crossTypeFactoriesReady.await(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "cross-type factory coordination interrupted",
                    interrupted
            );
        }
    }

    private static ExecutorService newDaemonExecutor() {
        return newDaemonExecutor(1, "capability-reentry-test");
    }

    private static ExecutorService newDaemonExecutor(int threadCount, String threadName) {
        return Executors.newFixedThreadPool(threadCount, task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void assertFastIllegalState(Future<Boolean> result) throws Exception {
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> result.get(1, TimeUnit.SECONDS)
        );
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    private static void awaitWaiting(AtomicReference<Thread> threadReference)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
            if (thread != null && (thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING)) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("pending waiter did not enter a waiting state");
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, List<?>> eventBusListeners() throws ReflectiveOperationException {
        Field field = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("listeners");
        field.setAccessible(true);
        return (Map<Object, List<?>>) field.get(MinecraftForge.EVENT_BUS);
    }

    private static final class CoordinatedStorage
            extends ConcurrentHashMap<Class<? extends MapCapability>, MapCapability> {
        private final CyclicBarrier barrier;
        private final Class<?> target;
        private final AtomicInteger targetContainsCalls = new AtomicInteger();

        private CoordinatedStorage(CyclicBarrier barrier, Class<?> target) {
            this.barrier = barrier;
            this.target = target;
        }

        @Override
        public boolean containsKey(Object key) {
            boolean present = super.containsKey(key);
            if (target.equals(key) && targetContainsCalls.incrementAndGet() <= 4) {
                awaitBarrier(barrier);
            }
            return present;
        }
    }

    private static final class BlockingPublishedLookupStorage
            extends ConcurrentHashMap<Class<? extends MapCapability>, MapCapability> {
        private final Class<?> target;
        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch lookupEntered = new CountDownLatch(1);
        private final CountDownLatch releaseLookup = new CountDownLatch(1);

        private BlockingPublishedLookupStorage(Class<?> target) {
            this.target = target;
        }

        private void arm() {
            armed.set(true);
        }

        private void release() {
            releaseLookup.countDown();
        }

        @Override
        public boolean containsKey(Object key) {
            boolean present = super.containsKey(key);
            blockFirstLookup(key);
            return present;
        }

        @Override
        public MapCapability get(Object key) {
            MapCapability capability = super.get(key);
            blockFirstLookup(key);
            return capability;
        }

        private void blockFirstLookup(Object key) {
            if (target.equals(key) && armed.compareAndSet(true, false)) {
                lookupEntered.countDown();
                awaitLatch(releaseLookup, "published capability lookup");
            }
        }
    }

    private static final class ConcurrentLifecycleCapability extends MapCapability {
        private ConcurrentLifecycleCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            INITIALIZATIONS.incrementAndGet();
        }

    }

    private static final class InitFailingCapability extends MapCapability {
        private InitFailingCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            throw new IllegalStateException("fixture initialization failed");
        }

        @Override
        public void destroy() {
            DESTROYS.incrementAndGet();
        }
    }

    private static final class RegistrationFailingCapability extends MapCapability {
        private RegistrationFailingCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            REGISTRATION_FAILURE_INITIALIZATIONS.incrementAndGet();
        }

        @Override
        public void destroy() {
            REGISTRATION_FAILURE_DESTROYS.incrementAndGet();
        }

        @SubscribeEvent
        public void invalidListener() {
        }
    }

    public static final class EventVisibilityCapability extends MapCapability {
        public EventVisibilityCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
        }

        @SubscribeEvent
        public void onProbe(ProbeEvent ignored) {
            PROBE_EVENTS.incrementAndGet();
        }
    }

    public static final class ProbeEvent extends Event {
        public ProbeEvent() {
        }
    }

    private static final class CrossTypeCycleACapability extends MapCapability {
        private CrossTypeCycleACapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            CROSS_TYPE_MAP.get().getOrCreate(CrossTypeCycleBCapability.class);
        }
    }

    private static final class CrossTypeCycleBCapability extends MapCapability {
        private CrossTypeCycleBCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            CROSS_TYPE_MAP.get().getOrCreate(CrossTypeCycleACapability.class);
        }
    }

    private static final class CrossTypeParentCapability extends MapCapability {
        private CrossTypeParentCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            CROSS_TYPE_MAP.get().getOrCreate(CrossTypeDependencyCapability.class)
                    .orElseThrow();
        }
    }

    private static final class CrossTypeDependencyCapability extends MapCapability {
        private CrossTypeDependencyCapability(BaseMap map) {
            super(map);
        }
    }

    private static final class BlockingInitCapability extends MapCapability {
        private volatile boolean initialized;

        private BlockingInitCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            BLOCKING_INITIALIZATIONS.incrementAndGet();
            blockingInitEntered.countDown();
            try {
                if (!releaseBlockingInit.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("blocking init fixture timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("blocking init fixture interrupted", interrupted);
            }
            initialized = true;
        }

        @Override
        public void destroy() {
            BLOCKING_DESTROYS.incrementAndGet();
            initialized = false;
        }
    }

    private static final class ReentrantFactoryCapability extends MapCapability {
        private ReentrantFactoryCapability(BaseMap map) {
            super(map);
        }
    }

    private static final class ReentrantInitCapability extends MapCapability {
        private ReentrantInitCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void init() {
            REENTRANT_MAP.get().getOrCreate(ReentrantInitCapability.class);
        }
    }

    private static final class PersistentClaimCapability extends MapCapability
            implements FPSMCapability.Savable<String> {
        private String value;
        private String valueSeenDuringInit;

        private PersistentClaimCapability(BaseMap map) {
            super(map);
        }

        @Override
        public Codec<String> codec() {
            return Codec.STRING;
        }

        @Override
        public String write(String value) {
            this.value = value;
            return value;
        }

        @Override
        public String read() {
            return value;
        }

        @Override
        public void init() {
            valueSeenDuringInit = value;
        }
    }

    private static final class DestroyFailingCapability extends MapCapability {
        private boolean failUnregister;
        private boolean failHashCode;

        private DestroyFailingCapability(BaseMap map) {
            super(map);
        }

        @Override
        public void destroy() {
            failHashCode = failUnregister;
            throw new IllegalStateException("fixture destroy failed");
        }

        @Override
        public int hashCode() {
            if (failHashCode) {
                throw new IllegalArgumentException("fixture unregister failed");
            }
            return System.identityHashCode(this);
        }
    }

    private static final class LifecycleAwareSavableCapability extends MapCapability
            implements FPSMCapability.Savable<String> {
        private String value;
        private volatile boolean destroyed;

        private LifecycleAwareSavableCapability(BaseMap map) {
            super(map);
        }

        @Override
        public Codec<String> codec() {
            lifecycleDecodeEntered.countDown();
            awaitLatch(releaseLifecycleDecode, "lifecycle decode");
            return Codec.STRING;
        }

        @Override
        public JsonElement toJson() {
            lifecycleSerializeEntered.countDown();
            awaitLatch(releaseLifecycleSerialize, "lifecycle serialize");
            if (destroyed) {
                SERIALIZE_AFTER_DESTROY.incrementAndGet();
            }
            return new JsonPrimitive(value == null ? "" : value);
        }

        @Override
        public String write(String value) {
            if (destroyed) {
                DECODE_AFTER_DESTROY.incrementAndGet();
            }
            this.value = value;
            return value;
        }

        @Override
        public String read() {
            return value;
        }

        @Override
        public void init() {
            lifecycleInitEntered.countDown();
            awaitLatch(releaseLifecycleInit, "lifecycle init");
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private record InterruptedWaitResult(Throwable failure, boolean interrupted) {
    }
}
