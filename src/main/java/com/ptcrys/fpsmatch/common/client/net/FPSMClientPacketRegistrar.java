package com.ptcrys.fpsmatch.common.client.net;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapBootstrap;
import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.ptcrys.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalDiagnosticSnapshot;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalOpenRequest;
import com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.Ldlib2TacticalScreenOpener;
import com.ptcrys.fpsmatch.common.client.key.MinimapTacticalKey;
import com.ptcrys.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.ptcrys.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.ptcrys.fpsmatch.common.packet.ClientPacketRegistry;
import com.ptcrys.fpsmatch.common.packet.FPSMInventorySelectedS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMatchRespawnS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMusicPlayS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.ptcrys.fpsmatch.common.packet.OpenMatchConfigToolScreenS2CPacket;
import com.ptcrys.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.ptcrys.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.ptcrys.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.ptcrys.fpsmatch.common.packet.shop.OpenShopConfigToolScreenS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomReadyStateS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.ptcrys.fpsmatch.common.packet.shop.ShopDataSlotS2CPacket;
import com.ptcrys.fpsmatch.common.packet.shop.ShopActionResultS2CPacket;
import com.ptcrys.fpsmatch.common.packet.shop.ShopMoneyS2CPacket;
import com.ptcrys.fpsmatch.common.packet.team.FPSMAddTeamS2CPacket;
import com.ptcrys.fpsmatch.common.packet.team.TeamCapabilitiesS2CPacket;
import com.ptcrys.fpsmatch.common.packet.team.TeamManageResultS2CPacket;
import com.ptcrys.fpsmatch.common.packet.team.TeamPlayerLeaveS2CPacket;
import com.ptcrys.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import com.ptcrys.fpsmatch.common.packet.spec.SpectatorTargetS2CPacket;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapC2SPacket;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapPacketLifecycle;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapPacketSender;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.ptcrys.fpsmatch.common.packet.minimap.MinimapS2CPacket;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.compat.spectate.net.SpectatorInspectPackets;
import com.ptcrys.fpsmatch.compat.spectate.net.SpectatorLrtAttackPackets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class FPSMClientPacketRegistrar {
    private static final AcceptanceTacticalHolder ACCEPTANCE_TACTICAL =
            new AcceptanceTacticalHolder();
    private static volatile boolean registered;

    private FPSMClientPacketRegistrar() {
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        ForgeMinimapClientLifecycleEventSource minimapEvents =
                new ForgeMinimapClientLifecycleEventSource(
                        MinecraftForge.EVENT_BUS
                );
        MinimapPacketLifecycle.bindClient(minimapEvents);
        long cacheBytes = FPSMConfig.client.minimapCacheMiB.get().longValue()
                * 1024L * 1024L;
        ClientMinimapServices minimapServices = installMinimap(
                new MinimapDiskCache(
                        minimapCacheRoot(FMLLoader.getGamePath()), cacheBytes
                ),
                FPSMatch::sendToServer,
                System::currentTimeMillis,
                UUID::randomUUID,
                minimapEvents,
                MinimapS2CPacket::installDispatcher
        );
        FPSMGameHudManager.INSTANCE.installMinimap(minimapServices);
        TacticalMapController tacticalController = new TacticalMapController(
                minimapServices.runtime()
        );
        MinimapClientScreens screens = new MinimapClientScreens(
                tacticalController,
                minimapServices.subscriptions(),
                new Ldlib2TacticalScreenOpener(
                        FPSMGameHudManager.INSTANCE::minimapHudPresentation
                )
        );
        if (!installAcceptanceTactical(minimapServices, screens)) {
            throw new IllegalStateException(
                    "Conflicting minimap services/screens installation"
            );
        }
        MinimapTacticalKey.install(screens);

        ClientPacketRegistry.register(OpenMapCreatorToolScreenS2CPacket.class, FPSMClientPacketHandlers::handleOpenMapCreatorToolScreen);
        ClientPacketRegistry.register(OpenMatchConfigToolScreenS2CPacket.class, FPSMClientPacketHandlers::handleOpenMatchConfigToolScreen);
        ClientPacketRegistry.register(OpenSpawnPointToolScreenS2CPacket.class, FPSMClientPacketHandlers::handleOpenSpawnPointToolScreen);
        ClientPacketRegistry.register(OpenShopConfigToolScreenS2CPacket.class, FPSMClientPacketHandlers::handleOpenShopConfigToolScreen);
        ClientPacketRegistry.register(FPSMatchRespawnS2CPacket.class, FPSMClientPacketHandlers::handleRespawn);
        ClientPacketRegistry.register(FPSMInventorySelectedS2CPacket.class, FPSMClientPacketHandlers::handleInventorySelected);
        ClientPacketRegistry.register(AddAreaDataS2CPacket.class, FPSMClientPacketHandlers::handleAddAreaData);
        ClientPacketRegistry.register(AddPointDataS2CPacket.class, FPSMClientPacketHandlers::handleAddPointData);
        ClientPacketRegistry.register(FPSMatchGameTypeS2CPacket.class, FPSMClientPacketHandlers::handleGameType);
        ClientPacketRegistry.register(FPSMatchStatsResetS2CPacket.class, FPSMClientPacketHandlers::handleStatsReset);
        ClientPacketRegistry.register(FPSMSoundPlayS2CPacket.class, FPSMClientPacketHandlers::handleSoundPlay);
        ClientPacketRegistry.register(FPSMusicPlayS2CPacket.class, FPSMClientPacketHandlers::handleMusicPlay);
        ClientPacketRegistry.register(FPSMusicStopS2CPacket.class, FPSMClientPacketHandlers::handleMusicStop);
        ClientPacketRegistry.register(RemoveDebugDataByPrefixS2CPacket.class, FPSMClientPacketHandlers::handleRemoveDebugDataByPrefix);
        ClientPacketRegistry.register(ShopDataSlotS2CPacket.class, FPSMClientPacketHandlers::handleShopDataSlot);
        ClientPacketRegistry.register(ShopActionResultS2CPacket.class, FPSMClientPacketHandlers::handleShopActionResult);
        ClientPacketRegistry.register(ShopMoneyS2CPacket.class, FPSMClientPacketHandlers::handleShopMoney);
        ClientPacketRegistry.register(FPSMAddTeamS2CPacket.class, FPSMClientPacketHandlers::handleAddTeam);
        ClientPacketRegistry.register(TeamCapabilitiesS2CPacket.class, FPSMClientPacketHandlers::handleTeamCapabilities);
        ClientPacketRegistry.register(TeamPlayerLeaveS2CPacket.class, FPSMClientPacketHandlers::handleTeamPlayerLeave);
        ClientPacketRegistry.register(TeamPlayerStatsS2CPacket.class, FPSMClientPacketHandlers::handleTeamPlayerStats);
        ClientPacketRegistry.register(MapSelectionAccessS2CPacket.class, FPSMClientPacketHandlers::handleMapSelectionAccess);
        ClientPacketRegistry.register(MapSelectionSnapshotS2CPacket.class, FPSMClientPacketHandlers::handleMapSelectionSnapshot);
        ClientPacketRegistry.register(MapRoomDetailS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomDetail);
        ClientPacketRegistry.register(MapRoomReadyStateS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomReadyState);
        ClientPacketRegistry.register(MapRoomToastS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomToast);
        ClientPacketRegistry.register(MapRoomInvitationS2CPacket.class, FPSMClientPacketHandlers::handleMapRoomInvitation);
        ClientPacketRegistry.register(TeamManageResultS2CPacket.class, FPSMClientPacketHandlers::handleTeamManageResult);
        ClientPacketRegistry.register(SpectatorTargetS2CPacket.class, SpectatorTargetClientHandler::handle);
        ClientPacketRegistry.register(SpectatorInspectPackets.S2CWatchedPlayerInspectPacket.class, SpectatorClientPacketHandlers::handleWatchedPlayerInspect);
        ClientPacketRegistry.register(SpectatorLrtAttackPackets.S2CWatchedPlayerLrtAttackPacket.class, SpectatorClientPacketHandlers::handleWatchedPlayerLrtAttack);
    }

    static ClientMinimapServices installMinimap(
            MinimapDiskCache diskCache,
            Consumer<? super MinimapC2SPacket> transport,
            LongSupplier nowMillis,
            Supplier<UUID> requestIds,
            ClientMinimapBootstrap.EventSource events,
            Consumer<MinimapS2CDispatcher> dispatcherInstaller
    ) {
        ClientMinimapServices services = ClientMinimapServices.create(
                diskCache,
                message -> sendMinimap(message, requestIds, transport),
                nowMillis,
                requestIds
        );
        ClientMinimapBootstrap.EventSource lifecycle = (
                onConnect, onDisconnect, onReset, onTick
        ) -> events.bind(
                (token, serverIdentity) -> {
                    onConnect.accept(token, serverIdentity);
                    ACCEPTANCE_TACTICAL.lifecycleChanged(services);
                },
                token -> {
                    onDisconnect.accept(token);
                    ACCEPTANCE_TACTICAL.lifecycleChanged(services);
                },
                () -> {
                    onReset.run();
                    ACCEPTANCE_TACTICAL.lifecycleChanged(services);
                },
                () -> {
                    onTick.run();
                    ACCEPTANCE_TACTICAL.tick(services);
                }
        );
        new ClientMinimapBootstrap(
                services, dispatcherInstaller
        ).install(lifecycle);
        return services;
    }

    static Path minimapCacheRoot(Path gamePath) {
        return gamePath.resolve("fpsmatch").resolve("cache").resolve("minimap");
    }

    private static void sendMinimap(
            MinimapWireMessage message,
            Supplier<UUID> frameIds,
            Consumer<? super MinimapC2SPacket> transport
    ) {
        MinimapPacketSender.sendC2S(frameIds.get(), message, transport);
    }

    public static ClientMinimapServices minimapServices() {
        return ACCEPTANCE_TACTICAL.services();
    }

    static boolean installAcceptanceTactical(
            ClientMinimapServices services,
            MinimapClientScreens screens
    ) {
        return ACCEPTANCE_TACTICAL.install(services, screens);
    }

    public static Optional<RuntimeGeneration> currentAcceptanceTacticalGeneration() {
        return ACCEPTANCE_TACTICAL.currentGeneration();
    }

    public static boolean openAcceptanceTactical(
            RuntimeGeneration expected,
            TacticalOpenRequest request
    ) {
        return ACCEPTANCE_TACTICAL.open(expected, request);
    }

    public static AcceptanceTacticalState acceptanceTacticalState(
            RuntimeGeneration expected
    ) {
        return ACCEPTANCE_TACTICAL.state(expected);
    }

    public static Optional<TacticalDiagnosticSnapshot> acceptanceTacticalDiagnostic(
            RuntimeGeneration expected
    ) {
        return ACCEPTANCE_TACTICAL.diagnostic(expected);
    }

    public static boolean closeAcceptanceTactical(RuntimeGeneration expected) {
        return ACCEPTANCE_TACTICAL.close(expected);
    }

    public static boolean ownsAcceptanceTacticalScreen(
            RuntimeGeneration expected,
            Object screen
    ) {
        return ACCEPTANCE_TACTICAL.ownsScreen(expected, screen);
    }

    static Optional<UUID> probeMatchHud(
            ClientMinimapServices services,
            String gameType,
            String mapName,
            String dimension
    ) {
        try {
            return Objects.requireNonNull(services, "services")
                    .subscriptions()
                    .enterMatch(new WireIdentity.MapTarget(
                            new MapKey(gameType, mapName),
                            NamespacedId.parse(dimension)
                    ));
        } catch (IllegalArgumentException invalidTarget) {
            return Optional.empty();
        }
    }

    static void probeMatchHud(
            String gameType,
            String mapName,
            String dimension
    ) {
        ClientMinimapServices services = minimapServices();
        if (services != null) {
            probeMatchHud(services, gameType, mapName, dimension);
        }
    }

    public enum AcceptanceTacticalState {
        UNAVAILABLE,
        PENDING,
        READY
    }

    static final class AcceptanceTacticalHolder {
        private static final int MAX_PENDING_TICKS = 100;

        private final TacticalDiagnosticTracker diagnosticTracker =
                new TacticalDiagnosticTracker();
        private volatile ClientMinimapServices services;
        private volatile MinimapClientScreens screens;
        private volatile RuntimeGeneration ownerGeneration;
        private int pendingTicks;

        synchronized boolean install(
                ClientMinimapServices services,
                MinimapClientScreens screens
        ) {
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(screens, "screens");
            if (this.services == null && this.screens == null) {
                this.services = services;
                this.screens = screens;
                return true;
            }
            return this.services == services && this.screens == screens;
        }

        synchronized ClientMinimapServices services() {
            return services;
        }

        synchronized Optional<RuntimeGeneration> currentGeneration() {
            ClientMinimapServices currentServices = services;
            if (currentServices == null) {
                return Optional.empty();
            }
            return currentServices.runtime().currentGeneration()
                    .filter(generation -> isReadyRuntime(
                            currentServices, generation
                    ));
        }

        synchronized boolean open(
                RuntimeGeneration expected,
                TacticalOpenRequest request
        ) {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(request, "request");
            if (!isCurrent(expected)) {
                return false;
            }
            RuntimeGeneration owner = ownerGeneration;
            if (owner != null) {
                if (isCurrent(owner)
                        && stateInternal(owner)
                        != AcceptanceTacticalState.UNAVAILABLE) {
                    return false;
                }
                clearOwner(owner);
            }
            MinimapClientScreens currentScreens = screens;
            if (currentScreens == null) {
                return false;
            }
            long attemptSequence = diagnosticTracker.begin(expected);
            Consumer<TacticalDiagnosticSnapshot> diagnostics = snapshot ->
                    diagnosticTracker.publish(
                            expected, attemptSequence, snapshot
                    );
            final boolean opened;
            try {
                opened = currentScreens.openAcceptance(
                        request, attemptSequence, diagnostics
                );
            } catch (RuntimeException failure) {
                cleanupFailedRequest(failure);
                throw failure;
            }
            if (!opened) {
                return false;
            }
            if (!isCurrent(expected)) {
                currentScreens.close();
                diagnosticTracker.resetLifecycle();
                return false;
            }
            ownerGeneration = expected;
            pendingTicks = 0;
            return true;
        }

        synchronized AcceptanceTacticalState state(
                RuntimeGeneration expected
        ) {
            Objects.requireNonNull(expected, "expected");
            if (!expected.equals(ownerGeneration)) {
                return AcceptanceTacticalState.UNAVAILABLE;
            }
            return stateInternal(expected);
        }

        synchronized Optional<TacticalDiagnosticSnapshot> diagnostic(
                RuntimeGeneration expected
        ) {
            Objects.requireNonNull(expected, "expected");
            return diagnosticTracker.snapshot(expected);
        }

        synchronized boolean close(RuntimeGeneration expected) {
            Objects.requireNonNull(expected, "expected");
            if (!expected.equals(ownerGeneration)) {
                return false;
            }
            return retire(expected);
        }

        synchronized boolean ownsScreen(
                RuntimeGeneration expected,
                Object screen
        ) {
            Objects.requireNonNull(expected, "expected");
            MinimapClientScreens currentScreens = screens;
            return expected.equals(ownerGeneration)
                    && currentScreens != null
                    && currentScreens.ownsScreen(screen);
        }

        synchronized void tick(ClientMinimapServices source) {
            if (services != source) {
                return;
            }
            RuntimeGeneration owner = ownerGeneration;
            if (owner == null) {
                return;
            }
            AcceptanceTacticalState state = stateInternal(owner);
            if (state == AcceptanceTacticalState.PENDING) {
                pendingTicks++;
                if (pendingTicks >= MAX_PENDING_TICKS) {
                    close(owner);
                }
            } else if (state == AcceptanceTacticalState.READY) {
                pendingTicks = 0;
            } else {
                retire(owner);
            }
        }

        synchronized void lifecycleChanged(ClientMinimapServices source) {
            if (services != source) {
                return;
            }
            RuntimeGeneration owner = ownerGeneration;
            if (owner != null && !isCurrent(owner)) {
                retire(owner);
            }
            RuntimeGeneration current = source.runtime()
                    .currentGeneration().orElse(null);
            if (!diagnosticTracker.isBoundTo(current)) {
                diagnosticTracker.resetLifecycle();
            }
        }

        private void cleanupFailedRequest(RuntimeException failure) {
            ClientMinimapServices currentServices = services;
            if (currentServices == null) {
                return;
            }
            try {
                currentServices.unsubscribe(WireIdentity.Scope.TACTICAL_SCREEN);
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }

        private boolean retire(RuntimeGeneration expected) {
            MinimapClientScreens currentScreens = screens;
            boolean sameGeneration = isSameGeneration(expected);
            if (!sameGeneration
                    || currentScreens == null
                    || !isSameGeneration(expected)) {
                clearOwner(expected);
                return false;
            }
            try {
                currentScreens.close();
                return true;
            } finally {
                clearOwner(expected);
            }
        }

        private AcceptanceTacticalState stateInternal(
                RuntimeGeneration expected
        ) {
            ClientMinimapServices currentServices = services;
            MinimapClientScreens currentScreens = screens;
            if (currentServices == null
                    || currentScreens == null
                    || !isCurrent(expected)) {
                return AcceptanceTacticalState.UNAVAILABLE;
            }
            if (currentScreens.isOpenPending()) {
                return currentServices.hasScope(
                        WireIdentity.Scope.TACTICAL_SCREEN
                ) && !currentScreens.controller().isOpen()
                        ? AcceptanceTacticalState.PENDING
                        : AcceptanceTacticalState.UNAVAILABLE;
            }
            return currentServices.hasActiveScope(
                    WireIdentity.Scope.TACTICAL_SCREEN
            ) && currentScreens.controller().isOpen()
                    ? AcceptanceTacticalState.READY
                    : AcceptanceTacticalState.UNAVAILABLE;
        }

        private boolean isCurrent(RuntimeGeneration expected) {
            ClientMinimapServices currentServices = services;
            return currentServices != null
                    && currentServices.runtime().currentGeneration()
                    .filter(expected::equals)
                    .isPresent()
                    && isReadyRuntime(currentServices, expected)
                    && currentServices.runtime().currentGeneration()
                    .filter(expected::equals)
                    .isPresent();
        }

        private boolean isSameGeneration(RuntimeGeneration expected) {
            ClientMinimapServices currentServices = services;
            return currentServices != null
                    && currentServices.runtime().currentGeneration()
                    .filter(expected::equals)
                    .isPresent();
        }

        private void clearOwner(RuntimeGeneration expected) {
            if (expected.equals(ownerGeneration)) {
                ownerGeneration = null;
                pendingTicks = 0;
            }
        }

        private static boolean isReadyRuntime(
                ClientMinimapServices services,
                RuntimeGeneration expected
        ) {
            return services.hasActiveScope(WireIdentity.Scope.MATCH_HUD)
                    && services.activeRuntime()
                    .filter(active -> matches(active, expected))
                    .isPresent();
        }

        private static boolean matches(
                RuntimeEntryStore.ActiveRuntime active,
                RuntimeGeneration expected
        ) {
            return active.serverIdentity().equals(expected.serverIdentity())
                    && active.dimension().equals(expected.dimension())
                    && active.mapKey().equals(expected.mapKey())
                    && active.documentId().equals(expected.documentId())
                    && active.revision() == expected.revision()
                    && active.runtimeHash().equals(expected.runtimeHash());
        }
    }

    static final class TacticalDiagnosticTracker {
        private long nextAttemptSequence;
        private long lifecycleSequence;
        private long activeLifecycleSequence;
        private long activeAttemptSequence;
        private RuntimeGeneration generation;
        private TacticalDiagnosticSnapshot snapshot;
        private boolean terminal;

        synchronized long begin(RuntimeGeneration generation) {
            this.generation = Objects.requireNonNull(generation, "generation");
            activeLifecycleSequence = lifecycleSequence;
            activeAttemptSequence = ++nextAttemptSequence;
            snapshot = null;
            terminal = false;
            return activeAttemptSequence;
        }

        synchronized void publish(
                RuntimeGeneration generation,
                long attemptSequence,
                TacticalDiagnosticSnapshot snapshot
        ) {
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(snapshot, "snapshot");
            if (terminal
                    || activeLifecycleSequence != lifecycleSequence
                    || !generation.equals(this.generation)
                    || attemptSequence != activeAttemptSequence
                    || snapshot.attemptSequence() != attemptSequence) {
                return;
            }
            this.snapshot = snapshot;
            terminal = snapshot.stage().terminal();
        }

        synchronized Optional<TacticalDiagnosticSnapshot> snapshot(
                RuntimeGeneration expected
        ) {
            Objects.requireNonNull(expected, "expected");
            return expected.equals(generation)
                    ? Optional.ofNullable(snapshot)
                    : Optional.empty();
        }

        synchronized boolean isBoundTo(RuntimeGeneration current) {
            return generation == null || generation.equals(current);
        }

        synchronized void resetLifecycle() {
            lifecycleSequence++;
            activeLifecycleSequence = lifecycleSequence;
            activeAttemptSequence = 0L;
            generation = null;
            snapshot = null;
            terminal = false;
        }
    }
}
