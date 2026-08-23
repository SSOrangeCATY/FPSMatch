package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface EditorWireMessage extends MinimapWireMessage
        permits EditorWireMessage.EditorOpen, EditorWireMessage.EditorResume,
        EditorWireMessage.RequestSourceEntries, EditorWireMessage.EditorOperation,
        EditorWireMessage.UploadFragment, EditorWireMessage.SaveDraft,
        EditorWireMessage.EditorClose, EditorWireMessage.EditorSession,
        EditorWireMessage.SourceManifest, EditorWireMessage.SourceFragment,
        EditorWireMessage.EditorAck {
    record EditorOpen(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.MapTarget target,
            NamespacedId documentId,
            WireEditor.OpenMode openMode,
            long expectedRevision,
            Optional<Sha256> expectedRuntimeHash
    ) implements EditorWireMessage {
        public EditorOpen {
            Objects.requireNonNull(requestId, "requestId");
            WireEditor.requireEditorLease(lease);
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(openMode, "openMode");
            if (expectedRevision < 0) {
                throw new IllegalArgumentException(
                        "Expected editor revision must be non-negative"
                );
            }
            Objects.requireNonNull(expectedRuntimeHash, "expectedRuntimeHash");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_OPEN;
        }
    }

    record EditorResume(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            UUID draftId,
            Sha256 draftRootHash,
            long ackCursor
    ) implements EditorWireMessage {
        public EditorResume {
            Objects.requireNonNull(requestId, "requestId");
            WireEditor.requireEditorLease(lease);
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(draftId, "draftId");
            Objects.requireNonNull(draftRootHash, "draftRootHash");
            if (ackCursor < 0) {
                throw new IllegalArgumentException("Editor ACK cursor must be non-negative");
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_RESUME;
        }
    }

    record RequestSourceEntries(
            UUID requestId,
            WireIdentity.EditorContext context,
            Sha256 sourceHash,
            List<WireTransfer.EntryRequest> entries
    ) implements EditorWireMessage {
        public RequestSourceEntries {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(entries, "entries");
            if (entries.size() > MinimapHardLimits.MAX_ENTRY_REQUESTS) {
                throw new IllegalArgumentException(
                        "Editor source entry request count exceeds 256"
                );
            }
            entries = List.copyOf(entries);
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_REQUEST_SOURCE_ENTRIES;
        }
    }

    record EditorOperation(
            UUID requestId,
            WireIdentity.EditorContext context,
            long opSequence,
            Sha256 expectedRootHash,
            Sha256 payloadHash,
            List<WireEditor.DraftMutation> mutations
    ) implements EditorWireMessage {
        public EditorOperation {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            if (opSequence < 0) {
                throw new IllegalArgumentException(
                        "Editor operation sequence must be non-negative"
                );
            }
            Objects.requireNonNull(expectedRootHash, "expectedRootHash");
            Objects.requireNonNull(payloadHash, "payloadHash");
            Objects.requireNonNull(mutations, "mutations");
            if (mutations.isEmpty()
                    || mutations.size() > MinimapHardLimits.MAX_EDITOR_MUTATIONS) {
                throw new IllegalArgumentException(
                        "Editor operation mutation count must be between 1 and 64"
                );
            }
            mutations = List.copyOf(mutations);
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_OPERATION;
        }
    }

    record UploadFragment(
            UUID requestId,
            WireIdentity.EditorContext context,
            WireEditor.UploadActionData data
    ) implements EditorWireMessage {
        public UploadFragment {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(data, "data");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_UPLOAD_FRAGMENT;
        }
    }

    record SaveDraft(
            UUID requestId,
            WireIdentity.EditorContext context,
            long expectedAckCursor,
            Sha256 expectedRootHash,
            boolean compact
    ) implements EditorWireMessage {
        public SaveDraft {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            if (expectedAckCursor < 0) {
                throw new IllegalArgumentException(
                        "Expected editor ACK cursor must be non-negative"
                );
            }
            Objects.requireNonNull(expectedRootHash, "expectedRootHash");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_SAVE_DRAFT;
        }
    }

    record EditorClose(
            UUID requestId,
            WireIdentity.EditorContext context,
            WireEditor.CloseMode closeMode
    ) implements EditorWireMessage {
        public EditorClose {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(closeMode, "closeMode");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_CLOSE;
        }
    }

    record EditorSession(
            UUID requestId,
            WireIdentity.EditorContext context,
            long expiresAtEpochMillis,
            WireEditor.SourceAvailability sourceAvailability
    ) implements EditorWireMessage {
        public EditorSession {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            if (expiresAtEpochMillis < 0) {
                throw new IllegalArgumentException(
                        "Editor session expiry must be non-negative"
                );
            }
            Objects.requireNonNull(sourceAvailability, "sourceAvailability");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_EDITOR_SESSION;
        }
    }

    record SourceManifest(
            UUID requestId,
            WireIdentity.EditorContext context,
            Sha256 sourceHash,
            Sha256 manifestHash,
            WireTransfer.TransferFragment transfer
    ) implements EditorWireMessage {
        public SourceManifest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(manifestHash, "manifestHash");
            Objects.requireNonNull(transfer, "transfer");
            if (transfer.totalLength() > MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES) {
                throw new IllegalArgumentException(
                        "Source manifest exceeds its transfer byte limit"
                );
            }
            if (!manifestHash.equals(transfer.objectHash())) {
                throw new IllegalArgumentException(
                        "Source manifest transfer hash must equal its manifest hash"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_EDITOR_SOURCE_MANIFEST;
        }
    }

    record SourceFragment(
            UUID requestId,
            WireIdentity.EditorContext context,
            Sha256 sourceHash,
            ContainerPath path,
            WireEditor.MediaType mediaType,
            WireTransfer.TransferFragment transfer
    ) implements EditorWireMessage {
        public SourceFragment {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(mediaType, "mediaType");
            Objects.requireNonNull(transfer, "transfer");
            long maximum = mediaType == WireEditor.MediaType.JSON
                    ? MinimapHardLimits.MAX_JSON_ENTRY_BYTES
                    : MinimapHardLimits.MAX_ZIP_ENTRY_BYTES;
            if (transfer.totalLength() > maximum) {
                throw new IllegalArgumentException(
                        "Source fragment exceeds its media byte limit"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_EDITOR_SOURCE_FRAGMENT;
        }
    }

    record EditorAck(
            UUID requestId,
            WireIdentity.EditorContext context,
            WireEditor.AckData data
    ) implements EditorWireMessage {
        public EditorAck {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(data, "data");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_EDITOR_ACK;
        }
    }
}
