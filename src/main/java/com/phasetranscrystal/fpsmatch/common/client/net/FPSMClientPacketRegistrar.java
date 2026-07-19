package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapBootstrap;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.MinimapClientScreens;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.Ldlib2TacticalScreenOpener;
import com.phasetranscrystal.fpsmatch.common.client.key.MinimapTacticalKey;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketRegistry;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMInventorySelectedS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchRespawnS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMatchConfigToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopConfigToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomReadyStateS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopMoneyS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.FPSMAddTeamS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamCapabilitiesS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageResultS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerLeaveS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SpectatorTargetS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapPacketLifecycle;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapPacketSender;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapS2CPacket;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorInspectPackets;
import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorLrtAttackPackets;
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
    private static boolean registered;
    private static ClientMinimapServices minimapServices;

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
        minimapServices = installMinimap(
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
        new ClientMinimapBootstrap(services, dispatcherInstaller).install(events);
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
        return minimapServices;
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
        ClientMinimapServices services = minimapServices;
        if (services != null) {
            probeMatchHud(services, gameType, mapName, dimension);
        }
    }
}
