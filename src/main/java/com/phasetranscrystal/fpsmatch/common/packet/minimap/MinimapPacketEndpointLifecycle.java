package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

final class MinimapPacketEndpointLifecycle {
    private final MinimapPacketEndpointRegistry registry;
    private final Map<Object, MinimapPacketEndpointRegistry.EndpointLease>
            serverConnections = new IdentityHashMap<>();
    private Object activeServer;
    private Object clientConnection;
    private MinimapPacketEndpointRegistry.EndpointLease clientLease;

    MinimapPacketEndpointLifecycle(MinimapPacketEndpointRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    void bindServer(MinimapPacketLifecycle.ServerEventSource source) {
        Objects.requireNonNull(source, "source");
        source.onServerStarted(this::serverStarted);
        source.onConnectionOpened(this::serverConnectionOpened);
        source.onConnectionClosed(this::serverConnectionClosed);
        source.onServerStopped(this::serverStopped);
    }

    void bindClient(MinimapPacketLifecycle.ClientEventSource source) {
        Objects.requireNonNull(source, "source");
        source.onLoggedIn(this::clientLoggedIn);
        source.onLoggedOut(this::clientLoggedOut);
        source.onReset(this::clientReset);
    }

    private synchronized void serverStarted(Object server) {
        Objects.requireNonNull(server, "server");
        closeServerConnections();
        activeServer = server;
    }

    private synchronized void serverConnectionOpened(
            Object server,
            Object connection
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(connection, "connection");
        if (activeServer != server) {
            return;
        }
        MinimapPacketEndpointRegistry.EndpointLease replacement =
                registry.install(connection);
        MinimapPacketEndpointRegistry.EndpointLease previous =
                serverConnections.put(connection, replacement);
        if (previous != null) {
            previous.close();
        }
    }

    private synchronized void serverConnectionClosed(Object connection) {
        if (connection == null) {
            return;
        }
        MinimapPacketEndpointRegistry.EndpointLease lease =
                serverConnections.remove(connection);
        if (lease != null) {
            lease.close();
        }
    }

    private synchronized void serverStopped(Object server) {
        if (server == null || activeServer != server) {
            return;
        }
        closeServerConnections();
        activeServer = null;
    }

    private synchronized void clientLoggedIn(Object connection) {
        Objects.requireNonNull(connection, "connection");
        closeClient();
        clientConnection = connection;
        clientLease = registry.install(connection);
    }

    private synchronized void clientLoggedOut(Object connection) {
        if (clientLease == null) {
            return;
        }
        if (connection == null || connection == clientConnection) {
            closeClient();
        }
    }

    private synchronized void clientReset() {
        if (clientLease != null && clientConnection != null) {
            clientLease = registry.install(clientConnection);
        }
    }

    private void closeServerConnections() {
        serverConnections.values().forEach(
                MinimapPacketEndpointRegistry.EndpointLease::close
        );
        serverConnections.clear();
    }

    private void closeClient() {
        if (clientLease != null) {
            clientLease.close();
        }
        clientLease = null;
        clientConnection = null;
    }
}
