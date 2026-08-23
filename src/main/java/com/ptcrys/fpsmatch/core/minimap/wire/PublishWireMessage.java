package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.UUID;

public sealed interface PublishWireMessage extends MinimapWireMessage
        permits PublishWireMessage.EditorRebase,
        PublishWireMessage.ReservePublish,
        PublishWireMessage.CommitPublish,
        PublishWireMessage.QueryPublishStatus,
        PublishWireMessage.PublishReservation,
        PublishWireMessage.EditorRebaseResult,
        PublishWireMessage.PublishResult,
        PublishWireMessage.PublishStatus,
        PublishWireMessage.ErrorMessage {
    record EditorRebase(
            UUID requestId,
            WireIdentity.EditorContext context,
            WireEditor.RebaseData data
    ) implements PublishWireMessage {
        public EditorRebase {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(data, "data");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_REBASE;
        }
    }

    record ReservePublish(
            UUID requestId,
            WireIdentity.EditorContext context
    ) implements PublishWireMessage {
        public ReservePublish {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_RESERVE_PUBLISH;
        }
    }

    record CommitPublish(
            UUID requestId,
            WireIdentity.EditorContext context,
            String publishToken,
            long publishRevision,
            UUID sourceUploadId,
            UUID runtimeUploadId,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) implements PublishWireMessage {
        public CommitPublish {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            publishToken = WireText.requireUtf8(
                    publishToken,
                    MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES,
                    "publishToken"
            );
            if (publishRevision < 0) {
                throw new IllegalArgumentException(
                        "Publish revision must be non-negative"
                );
            }
            Objects.requireNonNull(sourceUploadId, "sourceUploadId");
            Objects.requireNonNull(runtimeUploadId, "runtimeUploadId");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_COMMIT_PUBLISH;
        }
    }

    record QueryPublishStatus(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            String publishToken,
            long publishRevision
    ) implements PublishWireMessage {
        public QueryPublishStatus {
            Objects.requireNonNull(requestId, "requestId");
            WireEditor.requireEditorLease(lease);
            Objects.requireNonNull(binding, "binding");
            publishToken = WireText.requireUtf8(
                    publishToken,
                    MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES,
                    "publishToken"
            );
            if (publishRevision < 0) {
                throw new IllegalArgumentException(
                        "Publish revision must be non-negative"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_QUERY_PUBLISH_STATUS;
        }
    }

    record PublishReservation(
            UUID requestId,
            WireIdentity.EditorContext context,
            String publishToken,
            long baseRevision,
            long publishRevision,
            long expiresAtEpochMillis
    ) implements PublishWireMessage {
        public PublishReservation {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            publishToken = WireText.requireUtf8(
                    publishToken,
                    MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES,
                    "publishToken"
            );
            if (baseRevision < 0
                    || publishRevision < 0
                    || expiresAtEpochMillis < 0) {
                throw new IllegalArgumentException(
                        "Publish reservation revisions and expiry must be non-negative"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_PUBLISH_RESERVATION;
        }
    }

    record EditorRebaseResult(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            UUID rebaseId,
            WireStatus.RebaseResultStatus status,
            java.util.Optional<WireIdentity.EditorContext> newContext,
            long theirsRevision,
            Sha256 theirsHash,
            int pageIndex,
            int pageCount,
            java.util.List<WireEditor.Conflict> conflicts
    ) implements PublishWireMessage {
        public EditorRebaseResult {
            Objects.requireNonNull(requestId, "requestId");
            WireEditor.requireEditorLease(lease);
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(rebaseId, "rebaseId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(newContext, "newContext");
            if (theirsRevision < 0) {
                throw new IllegalArgumentException(
                        "Rebase-result revision must be non-negative"
                );
            }
            Objects.requireNonNull(theirsHash, "theirsHash");
            if (status == WireStatus.RebaseResultStatus.MERGED) {
                if (newContext.isEmpty() || pageIndex != 0 || pageCount != 1) {
                    throw new IllegalArgumentException(
                            "Merged rebase result has invalid context or page"
                    );
                }
            } else if (newContext.isPresent()
                    || pageCount <= 0
                    || pageCount > MinimapHardLimits.MAX_WIRE_PAGE_COUNT
                    || pageIndex < 0
                    || pageIndex >= pageCount) {
                throw new IllegalArgumentException(
                        "Conflict rebase result has invalid context or page"
                );
            }
            conflicts = WireCollections.copyBounded(
                    conflicts,
                    MinimapHardLimits.MAX_REBASE_ITEMS,
                    "Rebase conflicts"
            );
            if (status == WireStatus.RebaseResultStatus.MERGED
                    ? !conflicts.isEmpty()
                    : conflicts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Rebase-result status and conflicts do not agree"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_EDITOR_REBASE_RESULT;
        }
    }

    record PublishResult(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            String publishToken,
            long publishRevision,
            WireStatus.PublishOutcome outcome,
            java.util.Optional<WireStatus.HashTriple> hashes,
            java.util.Optional<WireStatus.ErrorInfo> error
    ) implements PublishWireMessage {
        public PublishResult {
            Objects.requireNonNull(requestId, "requestId");
            WireEditor.requireEditorLease(lease);
            Objects.requireNonNull(binding, "binding");
            publishToken = WireText.requireUtf8(
                    publishToken,
                    MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES,
                    "publishToken"
            );
            if (publishRevision < 0) {
                throw new IllegalArgumentException(
                        "Publish revision must be non-negative"
                );
            }
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(hashes, "hashes");
            Objects.requireNonNull(error, "error");
            boolean valid = outcome == WireStatus.PublishOutcome.COMMITTED
                    ? hashes.isPresent() && error.isEmpty()
                    : hashes.isEmpty() && error.isPresent();
            if (!valid) {
                throw new IllegalArgumentException(
                        "Publish outcome has invalid optional groups"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_PUBLISH_RESULT;
        }
    }

    record PublishStatus(
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            String publishToken,
            long publishRevision,
            WireStatus.PublishState state,
            java.util.Optional<Long> currentRevision,
            java.util.Optional<WireStatus.HashTriple> hashes,
            java.util.Optional<WireStatus.ErrorInfo> error
    ) implements PublishWireMessage {
        public PublishStatus {
            Objects.requireNonNull(requestId, "requestId");
            WireEditor.requireEditorLease(lease);
            Objects.requireNonNull(binding, "binding");
            publishToken = WireText.requireUtf8(
                    publishToken,
                    MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES,
                    "publishToken"
            );
            if (publishRevision < 0) {
                throw new IllegalArgumentException(
                        "Publish revision must be non-negative"
                );
            }
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(currentRevision, "currentRevision");
            Objects.requireNonNull(hashes, "hashes");
            Objects.requireNonNull(error, "error");
            if (currentRevision.isPresent() && currentRevision.orElseThrow() < 0) {
                throw new IllegalArgumentException(
                        "Current publish revision must be non-negative"
                );
            }
            boolean valid = switch (state) {
                case RESERVED, PREPARED -> currentRevision.isPresent()
                        && hashes.isEmpty() && error.isEmpty();
                case COMMITTED -> currentRevision.isPresent()
                        && hashes.isPresent() && error.isEmpty();
                case ABORTED -> currentRevision.isPresent()
                        && hashes.isEmpty() && error.isPresent();
                case STATUS_UNKNOWN -> currentRevision.isEmpty()
                        && hashes.isEmpty() && error.isPresent();
            };
            if (!valid) {
                throw new IllegalArgumentException(
                        "Publish state has invalid optional groups"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_PUBLISH_STATUS;
        }
    }

    record ErrorMessage(
            java.util.Optional<UUID> requestId,
            java.util.Optional<WireIdentity.ScopeLease> lease,
            java.util.Optional<WireIdentity.DocumentBinding> binding,
            java.util.Optional<Integer> failedOpcode,
            WireStatus.ErrorInfo error
    ) implements PublishWireMessage {
        public ErrorMessage {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(failedOpcode, "failedOpcode");
            if (failedOpcode.isPresent()
                    && (failedOpcode.orElseThrow() < 0
                    || failedOpcode.orElseThrow() > 0xff)) {
                throw new IllegalArgumentException(
                        "Failed opcode must fit an unsigned byte"
                );
            }
            Objects.requireNonNull(error, "error");
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_ERROR;
        }
    }
}
