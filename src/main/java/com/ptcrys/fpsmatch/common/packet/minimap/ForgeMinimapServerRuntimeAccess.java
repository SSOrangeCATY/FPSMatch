package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.common.minimap.server.sync.RuntimeAuthority;
import com.ptcrys.fpsmatch.common.minimap.server.sync.BuiltinRuntimeMapRegistry;
import com.ptcrys.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeFactory;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ForgeMinimapServerRuntimeAccess
        implements ServerMinimapRuntimeFactory.ServerAccess<MinecraftServer> {
    private final Supplier<UUID> frameIds;
    private final Supplier<BuiltinRuntimeMapRegistry> builtins;

    public ForgeMinimapServerRuntimeAccess(Supplier<UUID> frameIds) {
        this(frameIds, () -> BuiltinRuntimeMapRegistry.builder().build());
    }

    public ForgeMinimapServerRuntimeAccess(
            Supplier<UUID> frameIds,
            Supplier<BuiltinRuntimeMapRegistry> builtins
    ) {
        this.frameIds = Objects.requireNonNull(frameIds, "frameIds");
        this.builtins = Objects.requireNonNull(builtins, "builtins");
    }

    @Override
    public Path repositoryRoot(MinecraftServer server) {
        return Objects.requireNonNull(server, "server")
                .getWorldPath(LevelResource.ROOT)
                .resolve("fpsmatch")
                .resolve("minimaps");
    }

    @Override
    public Optional<RuntimeAuthority> resolveAuthority(
            MinecraftServer server,
            UUID actorId,
            WireIdentity.MapTarget target
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(target, "target");
        ServerPlayer player = server.getPlayerList().getPlayer(actorId);
        if (player == null || !FPSMCore.initialized()) {
            return Optional.empty();
        }
        try {
            Optional<BaseMap> currentMap = FPSMCore.getInstance()
                    .getMapByPlayerWithSpec(player);
            if (currentMap.isEmpty()) {
                return Optional.empty();
            }
            BaseMap map = currentMap.orElseThrow();
            MapKey mapKey = new MapKey(map.getGameType(), map.getMapName());
            NamespacedId mapDimension = NamespacedId.parse(
                    map.getServerLevel().dimension().location().toString()
            );
            NamespacedId playerDimension = NamespacedId.parse(
                    player.serverLevel().dimension().location().toString()
            );
            Optional<MinimapCapability> capability = map.getCapabilityMap()
                    .get(MinimapCapability.class);
            Optional<MinimapCapability.Binding> binding = capability
                    .flatMap(MinimapCapability::binding);
            Optional<RuntimeAuthority> builtin = Objects.requireNonNull(
                    builtins.get(), "builtin registry"
            ).find(target).map(entry -> new RuntimeAuthority(
                    target,
                    entry.declaration().documentId(),
                    0L,
                    entry.sourceHash(),
                    entry.runtimeHash()
            ));
            return authorityFor(
                    target,
                    mapKey,
                    mapDimension,
                    playerDimension,
                    capability.isPresent(),
                    map.checkGameHasPlayer(player) || map.checkSpecHasPlayer(player),
                    map.getServerLevel().getServer() == server,
                    binding,
                    builtin
            );
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public void send(
            MinecraftServer server,
            UUID actorId,
            MinimapWireMessage message
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(message, "message");
        ServerPlayer player = server.getPlayerList().getPlayer(actorId);
        if (player != null) {
            FPSMatch.sendToPlayer(
                    player,
                    MinimapS2CPacket.fromMessage(frameIds.get(), message)
            );
        }
    }

    static Optional<RuntimeAuthority> authorityFor(
            WireIdentity.MapTarget requested,
            MapKey currentMap,
            NamespacedId mapDimension,
            NamespacedId playerDimension,
            Optional<MinimapCapability.Binding> binding
    ) {
        return authorityFor(
                requested,
                currentMap,
                mapDimension,
                playerDimension,
                binding.isPresent(),
                true,
                true,
                binding,
                Optional.empty()
        );
    }

    static Optional<RuntimeAuthority> authorityFor(
            WireIdentity.MapTarget requested,
            MapKey currentMap,
            NamespacedId mapDimension,
            NamespacedId playerDimension,
            boolean capabilityMounted,
            boolean currentMembership,
            boolean currentServer,
            Optional<MinimapCapability.Binding> binding,
            Optional<RuntimeAuthority> builtin
    ) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(currentMap, "currentMap");
        Objects.requireNonNull(mapDimension, "mapDimension");
        Objects.requireNonNull(playerDimension, "playerDimension");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(builtin, "builtin");
        if (!capabilityMounted || !currentMembership || !currentServer
                || !requested.mapKey().equals(currentMap)
                || !requested.dimension().equals(mapDimension)
                || !playerDimension.equals(mapDimension)) {
            return Optional.empty();
        }
        Optional<RuntimeAuthority> published = binding
                .filter(value -> value.dimension().equals(mapDimension))
                .map(value -> new RuntimeAuthority(
                        requested,
                        value.documentId(),
                        value.revision(),
                        value.sourceHash(),
                        value.runtimeHash()
                ));
        if (binding.isPresent()) {
            return published;
        }
        return builtin.filter(authority -> authority.revision() == 0L
                && authority.target().equals(requested));
    }
}
