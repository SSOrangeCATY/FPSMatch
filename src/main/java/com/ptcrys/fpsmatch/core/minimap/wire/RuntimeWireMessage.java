package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface RuntimeWireMessage extends MinimapWireMessage
        permits RuntimeWireMessage.Subscribe, RuntimeWireMessage.Unsubscribe,
        RuntimeWireMessage.RequestEntries, RuntimeWireMessage.RequestMarkerReset,
        RuntimeWireMessage.ScopeAck, RuntimeWireMessage.Manifest,
        RuntimeWireMessage.EntryFragment {
    record Subscribe(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.MapTarget target,
            Optional<WireIdentity.RuntimeHint> runtimeHint
    ) implements RuntimeWireMessage {
        public Subscribe {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(runtimeHint, "runtimeHint");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Runtime subscriptions cannot use editor scope");
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_SUBSCRIBE;
        }
    }

    record Unsubscribe(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.MapTarget target
    ) implements RuntimeWireMessage {
        public Unsubscribe {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(target, "target");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Runtime subscriptions cannot use editor scope");
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_UNSUBSCRIBE;
        }
    }

    record RequestEntries(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            List<WireTransfer.EntryRequest> entries
    ) implements RuntimeWireMessage {
        public RequestEntries {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(entries, "entries");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Runtime entry requests cannot use editor scope");
            }
            if (entries.size() > 256) {
                throw new IllegalArgumentException("Runtime entry request count exceeds 256");
            }
            entries = List.copyOf(entries);
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_REQUEST_ENTRIES;
        }
    }

    record RequestMarkerReset(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            Optional<WireIdentity.MarkerStreamCursor> cursor
    ) implements RuntimeWireMessage {
        public RequestMarkerReset {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(cursor, "cursor");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Marker reset requests cannot use editor scope");
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_REQUEST_MARKER_RESET;
        }
    }

    record ScopeAck(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime
    ) implements RuntimeWireMessage {
        public ScopeAck {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(runtime, "runtime");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Runtime scope ACK cannot use editor scope");
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_SCOPE_ACK;
        }
    }

    record Manifest(
            Optional<UUID> requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            WireTransfer.TransferFragment transfer
    ) implements RuntimeWireMessage {
        public Manifest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(transfer, "transfer");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Runtime manifests cannot use editor scope");
            }
            if (!transfer.objectHash().equals(runtime.runtimeHash())) {
                throw new IllegalArgumentException(
                        "Manifest transfer hash must equal the runtime hash"
                );
            }
            if (transfer.totalLength() > MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES) {
                throw new IllegalArgumentException(
                        "Runtime manifest exceeds its hard byte limit"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_MANIFEST;
        }
    }

    record EntryFragment(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            ContainerPath path,
            WireTransfer.TransferFragment transfer
    ) implements RuntimeWireMessage {
        public EntryFragment {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(transfer, "transfer");
            if (lease.scope() == WireIdentity.Scope.EDITOR) {
                throw new IllegalArgumentException("Runtime entry fragments cannot use editor scope");
            }
            if (transfer.totalLength() > MinimapHardLimits.MAX_ZIP_ENTRY_BYTES) {
                throw new IllegalArgumentException("Runtime entry exceeds its hard byte limit");
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_ENTRY_FRAGMENT;
        }
    }
}
