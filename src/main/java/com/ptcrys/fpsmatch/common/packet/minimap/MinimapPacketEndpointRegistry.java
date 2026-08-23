package com.ptcrys.fpsmatch.common.packet.minimap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class MinimapPacketEndpointRegistry {
    private final MinimapFrameReassembler reassembler;
    private final Map<Object, EndpointLease> active = new IdentityHashMap<>();
    private long nextGeneration;

    MinimapPacketEndpointRegistry(MinimapFrameReassembler reassembler) {
        this.reassembler = Objects.requireNonNull(reassembler, "reassembler");
    }

    synchronized EndpointLease install(Object connectionToken) {
        Objects.requireNonNull(connectionToken, "connectionToken");
        if (nextGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("Packet endpoint generation exhausted");
        }
        long value = ++nextGeneration;
        EndpointLease replacement = new EndpointLease(
                this,
                new EndpointGeneration(this, connectionToken, value)
        );
        EndpointLease previous = active.put(connectionToken, replacement);
        if (previous != null) {
            reassembler.closeConnection(connectionToken);
        }
        return replacement;
    }

    synchronized Optional<EndpointGeneration> current(Object connectionToken) {
        EndpointLease lease = active.get(connectionToken);
        return lease == null ? Optional.empty() : Optional.of(lease.generation);
    }

    synchronized boolean isCurrent(EndpointGeneration generation) {
        if (generation == null || generation.owner != this) {
            return false;
        }
        EndpointLease current = active.get(generation.connectionToken);
        return current != null && current.generation.value == generation.value;
    }

    synchronized int activeEndpointCount() {
        return active.size();
    }

    synchronized void closeAll() {
        for (Object connectionToken : active.keySet()) {
            reassembler.closeConnection(connectionToken);
        }
        active.clear();
    }

    private synchronized void close(EndpointLease lease) {
        EndpointLease current = active.get(lease.generation.connectionToken);
        if (current == lease) {
            active.remove(lease.generation.connectionToken);
            reassembler.closeConnection(lease.generation.connectionToken);
        }
    }

    static final class EndpointGeneration {
        private final MinimapPacketEndpointRegistry owner;
        private final Object connectionToken;
        private final long value;

        private EndpointGeneration(
                MinimapPacketEndpointRegistry owner,
                Object connectionToken,
                long value
        ) {
            this.owner = owner;
            this.connectionToken = connectionToken;
            this.value = value;
        }

        long value() {
            return value;
        }

        Object connectionToken() {
            return connectionToken;
        }
    }

    static final class EndpointLease implements AutoCloseable {
        private final MinimapPacketEndpointRegistry owner;
        private final EndpointGeneration generation;

        private EndpointLease(
                MinimapPacketEndpointRegistry owner,
                EndpointGeneration generation
        ) {
            this.owner = owner;
            this.generation = generation;
        }

        EndpointGeneration generation() {
            return generation;
        }

        boolean isOpen() {
            return owner.isCurrent(generation);
        }

        @Override
        public void close() {
            owner.close(this);
        }
    }
}
