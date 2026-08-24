package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns one reserve/result/status-query cycle without leaking it into editor session state. */
final class EditorPublishLifecycle {
    private final WireIdentity.ScopeLease lease;
    private final WireIdentity.DocumentBinding binding;
    private final Consumer<MinimapWireMessage> sender;
    private final Supplier<UUID> requestIds;
    private final Map<UUID, EditorRequestKind> requests;

    private UUID resultRequestId;
    private UUID statusRequestId;
    private String publishToken;
    private long publishRevision = -1L;
    private boolean queryFailureNotified;

    EditorPublishLifecycle(
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            Consumer<MinimapWireMessage> sender,
            Supplier<UUID> requestIds,
            Map<UUID, EditorRequestKind> requests
    ) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    boolean inFlight() {
        return resultRequestId != null || statusRequestId != null;
    }

    void begin(WireIdentity.EditorContext context) {
        Objects.requireNonNull(context, "context");
        if (inFlight()) {
            throw new EditorCommandException("Publish already in progress");
        }
        resultRequestId = Objects.requireNonNull(requestIds.get(), "publish request ID");
        requests.put(resultRequestId, EditorRequestKind.PUBLISH);
        try {
            sender.accept(new PublishWireMessage.ReservePublish(resultRequestId, context));
        } catch (RuntimeException failure) {
            clear();
            throw failure;
        }
    }

    Optional<Resolution> accept(PublishWireMessage.PublishResult result) {
        Objects.requireNonNull(result, "result");
        if (resultRequestId == null || !resultRequestId.equals(result.requestId())
                || requests.get(result.requestId()) != EditorRequestKind.PUBLISH
                || !lease.equals(result.lease()) || !binding.equals(result.binding())) {
            return Optional.empty();
        }
        requests.remove(resultRequestId);
        resultRequestId = null;
        publishToken = result.publishToken();
        publishRevision = result.publishRevision();
        if (result.outcome() == WireStatus.PublishOutcome.STATUS_UNKNOWN) {
            return queryStatus();
        }
        Resolution resolution = new Resolution(
                result.outcome() == WireStatus.PublishOutcome.COMMITTED,
                result.publishRevision(), result.hashes(),
                result.error().map(WireStatus.ErrorInfo::detail).orElse("committed"),
                result.error().map(WireStatus.ErrorInfo::retryDisposition),
                true
        );
        clear();
        return Optional.of(resolution);
    }

    Optional<Resolution> accept(PublishWireMessage.PublishStatus status) {
        Objects.requireNonNull(status, "status");
        if (statusRequestId == null || !statusRequestId.equals(status.requestId())
                || requests.get(status.requestId()) != EditorRequestKind.PUBLISH_STATUS
                || !lease.equals(status.lease()) || !binding.equals(status.binding())
                || !Objects.equals(publishToken, status.publishToken())
                || publishRevision != status.publishRevision()) {
            return Optional.empty();
        }
        if (status.state() == WireStatus.PublishState.RESERVED
                || status.state() == WireStatus.PublishState.PREPARED) {
            return Optional.empty();
        }
        Resolution resolution = new Resolution(
                status.state() == WireStatus.PublishState.COMMITTED,
                status.currentRevision().orElse(status.publishRevision()), status.hashes(),
                status.error().map(WireStatus.ErrorInfo::detail).orElse("committed"),
                status.error().map(WireStatus.ErrorInfo::retryDisposition),
                true
        );
        clear();
        return Optional.of(resolution);
    }

    boolean accept(
            PublishWireMessage.ErrorMessage error,
            Consumer<Resolution> completion
    ) {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(completion, "completion");
        if (statusRequestId == null
                || error.requestId().filter(statusRequestId::equals).isEmpty()
                || error.lease().filter(lease::equals).isEmpty()
                || error.binding().filter(binding::equals).isEmpty()
                || error.failedOpcode().filter(code -> code
                == EditorRequestKind.PUBLISH_STATUS.opcode().code()).isEmpty()
                || requests.get(statusRequestId) != EditorRequestKind.PUBLISH_STATUS) {
            return false;
        }
        Resolution resolution = new Resolution(
                false, publishRevision, Optional.empty(), error.error().detail(),
                Optional.of(error.error().retryDisposition()), true
        );
        clear();
        completion.accept(resolution);
        return true;
    }

    void clear() {
        if (resultRequestId != null) requests.remove(resultRequestId);
        if (statusRequestId != null) requests.remove(statusRequestId);
        resultRequestId = null;
        statusRequestId = null;
        publishToken = null;
        publishRevision = -1L;
        queryFailureNotified = false;
    }

    boolean queryRequired() {
        return statusRequestId != null;
    }

    Optional<Resolution> retryStatusQuery() {
        if (!queryRequired()) {
            return Optional.empty();
        }
        try {
            sendStatusQuery();
            return Optional.empty();
        } catch (RuntimeException failure) {
            if (queryFailureNotified) {
                throw failure;
            }
            queryFailureNotified = true;
            return Optional.of(queryFailure(failure));
        }
    }

    private Optional<Resolution> queryStatus() {
        statusRequestId = Objects.requireNonNull(requestIds.get(), "publish status request ID");
        requests.put(statusRequestId, EditorRequestKind.PUBLISH_STATUS);
        try {
            sendStatusQuery();
            return Optional.empty();
        } catch (RuntimeException failure) {
            queryFailureNotified = true;
            return Optional.of(queryFailure(failure));
        }
    }

    private void sendStatusQuery() {
        sender.accept(new PublishWireMessage.QueryPublishStatus(
                statusRequestId, lease, binding, publishToken, publishRevision
        ));
    }

    private Resolution queryFailure(RuntimeException failure) {
        String detail = failure.getMessage() == null || failure.getMessage().isBlank()
                ? "publish status query failed"
                : "publish status query failed: " + failure.getMessage();
        return new Resolution(
                false, publishRevision, Optional.empty(), detail,
                Optional.of(WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS), false
        );
    }

    record Resolution(
            boolean committed,
            long revision,
            Optional<WireStatus.HashTriple> hashes,
            String detail,
            Optional<WireStatus.RetryDisposition> retryDisposition,
            boolean terminal
    ) {
        Resolution {
            if (revision < 0) throw new IllegalArgumentException("Publish revision must be non-negative");
            hashes = Objects.requireNonNull(hashes, "hashes");
            detail = detail == null || detail.isBlank() ? "publish failed" : detail;
            retryDisposition = Objects.requireNonNull(retryDisposition, "retryDisposition");
        }
    }
}
