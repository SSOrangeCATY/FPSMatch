package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.minimap.server.sync.BuiltinRuntimeCatalog;
import com.ptcrys.fpsmatch.common.minimap.server.sync.BuiltinRuntimeResourceLoader;
import com.ptcrys.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeBootstrap;
import com.ptcrys.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeFactory;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ForgeMinimapServerRuntimeRegistration {
    private ForgeMinimapServerRuntimeRegistration() {
    }

    public static void install(ForgeMinimapServerLifecycleEventSource events) {
        Objects.requireNonNull(events, "events");
        BuiltinRuntimeCatalog catalog = new BuiltinRuntimeCatalog(
                new BuiltinRuntimeResourceLoader()
        );
        ForgeMinimapServerRuntimeAccess access =
                new ForgeMinimapServerRuntimeAccess(
                        UUID::randomUUID,
                        catalog::snapshot
                );
        ServerMinimapRuntimeFactory<MinecraftServer> factory =
                new ServerMinimapRuntimeFactory<>(
                        access,
                        catalog::snapshot,
                        UUID::randomUUID,
                        () -> FPSMConfig.Server.minimapMarkerHz.get()
                );
        install(
                events,
                MinimapC2SPacket::installHandler,
                server -> factory.create((MinecraftServer) server),
                server -> reloadBuiltinCatalogLogged(
                        catalog,
                        (MinecraftServer) server
                ),
                catalog::clear
        );
    }

    static void reloadBuiltinCatalog(
            BuiltinRuntimeCatalog catalog,
            Path cacheRoot,
            ResourceManager resourceManager
    ) {
        Objects.requireNonNull(catalog, "catalog").reload(
                Objects.requireNonNull(cacheRoot, "cacheRoot"),
                ForgeBuiltinRuntimeResourceScanner.scan(
                        Objects.requireNonNull(resourceManager, "resourceManager")
                )
        );
    }

    private static void reloadBuiltinCatalogLogged(
            BuiltinRuntimeCatalog catalog,
            MinecraftServer server
    ) {
        try {
            Path cacheRoot = new ForgeMinimapServerRuntimeAccess(UUID::randomUUID)
                    .repositoryRoot(server)
                    .resolve("builtin-cache");
            reloadBuiltinCatalog(catalog, cacheRoot, server.getResourceManager());
        } catch (RuntimeException failure) {
            FPSMatch.LOGGER.error(
                    "Unable to reload builtin minimap catalog; keeping last good snapshot",
                    failure
            );
        }
    }

    static void install(
            ServerMinimapRuntimeBootstrap.EventSource events,
            Consumer<MinimapC2SRequestHandler> handlerInstaller,
            Function<Object, ServerMinimapRuntimeBootstrap.ActiveRuntime> runtimeFactory
    ) {
        ServerMinimapRuntimeBootstrap bootstrap = new ServerMinimapRuntimeBootstrap(
                handlerInstaller, runtimeFactory
        );
        bootstrap.install(Objects.requireNonNull(events, "events"));
    }

    static void install(
            ForgeMinimapServerLifecycleEventSource events,
            Consumer<MinimapC2SRequestHandler> handlerInstaller,
            Function<Object, ServerMinimapRuntimeBootstrap.ActiveRuntime> runtimeFactory,
            Consumer<Object> catalogLoader,
            Runnable catalogClearer
    ) {
        Objects.requireNonNull(events, "events").bindBuiltinCatalog(
                catalogLoader,
                catalogLoader,
                catalogClearer
        );
        install(events, handlerInstaller, runtimeFactory);
    }
}
