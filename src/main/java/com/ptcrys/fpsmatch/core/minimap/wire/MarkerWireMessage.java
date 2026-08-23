package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface MarkerWireMessage extends MinimapWireMessage
        permits MarkerWireMessage.Reset, MarkerWireMessage.Delta {
    int MAX_PAGE_ITEMS = 32;

    record Reset(
            Optional<UUID> requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            UUID streamEpoch,
            long sequence,
            UUID resetId,
            int pageIndex,
            int pageCount,
            List<WireMarker.Marker> markers
    ) implements MarkerWireMessage {
        public Reset {
            Objects.requireNonNull(requestId, "requestId");
            requireRuntimeScope(lease);
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(streamEpoch, "streamEpoch");
            if (sequence != 0) {
                throw new IllegalArgumentException("Marker reset sequence must be zero");
            }
            Objects.requireNonNull(resetId, "resetId");
            requirePage(pageIndex, pageCount);
            Objects.requireNonNull(markers, "markers");
            if (markers.size() > MAX_PAGE_ITEMS) {
                throw new IllegalArgumentException("Marker reset page exceeds 32 markers");
            }
            markers = List.copyOf(markers);
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_MARKER_RESET;
        }
    }

    record Delta(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            UUID streamEpoch,
            long sequence,
            List<WireMarker.DeltaOperation> operations
    ) implements MarkerWireMessage {
        public Delta {
            requireRuntimeScope(lease);
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(streamEpoch, "streamEpoch");
            if (sequence < 0) {
                throw new IllegalArgumentException("Marker delta sequence must be non-negative");
            }
            Objects.requireNonNull(operations, "operations");
            if (operations.size() > MAX_PAGE_ITEMS) {
                throw new IllegalArgumentException("Marker delta exceeds 32 operations");
            }
            operations = List.copyOf(operations);
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_MARKER_DELTA;
        }
    }

    private static void requireRuntimeScope(WireIdentity.ScopeLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (lease.scope() == WireIdentity.Scope.EDITOR) {
            throw new IllegalArgumentException("Marker messages cannot use editor scope");
        }
    }

    private static void requirePage(int pageIndex, int pageCount) {
        if (pageCount < 1 || pageCount > MinimapHardLimits.MAX_WIRE_PAGE_COUNT
                || pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("Marker page coordinates are invalid");
        }
    }
}
