package com.ptcrys.fpsmatch.common.minimap.server;

import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishDescriptor;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable, deterministic, non-wire identity for one editor publication attempt. */
public final class PublishAttemptReceipt {
    private static final int VERSION = 1;

    public enum Phase {
        RESERVED,
        EXPECTED_CAPTURED,
        PREPARED,
        COMMIT_ATTEMPTED,
        RESULT_READY,
        RESULT_RECORDED
    }

    private final UUID actorId;
    private final UUID requestId;
    private final WireIdentity.ScopeLease lease;
    private final WireIdentity.DocumentBinding binding;
    private final PublishTransaction reserved;
    private final Optional<PublishDescriptor> expectedDescriptor;
    private final Optional<PublishTransaction> prepared;
    private final Optional<WireStatus.HashTriple> hashes;
    private final Optional<PublishWireMessage.PublishResult> result;
    private final Phase phase;

    private PublishAttemptReceipt(
            UUID actorId,
            UUID requestId,
            WireIdentity.ScopeLease lease,
            WireIdentity.DocumentBinding binding,
            PublishTransaction reserved,
            Optional<PublishDescriptor> expectedDescriptor,
            Optional<PublishTransaction> prepared,
            Optional<WireStatus.HashTriple> hashes,
            Optional<PublishWireMessage.PublishResult> result,
            Phase phase
    ) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.lease = Objects.requireNonNull(lease, "lease");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.reserved = Objects.requireNonNull(reserved, "reserved");
        this.expectedDescriptor = Objects.requireNonNull(expectedDescriptor, "expectedDescriptor");
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.result = Objects.requireNonNull(result, "result");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public static PublishAttemptReceipt reserved(
            UUID actorId,
            PublishWireMessage.ReservePublish request,
            PublishTransaction transaction
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(transaction, "transaction");
        WireIdentity.EditorContext context = request.context();
        if (!transaction.mapKey().equals(context.binding().target().mapKey())
                || !transaction.dimension().equals(context.binding().target().dimension())
                || !transaction.documentId().equals(context.binding().documentId())
                || transaction.baseRevision() != context.baseRevision()
                || transaction.state()
                != com.ptcrys.fpsmatch.core.minimap.storage.PublishState.RESERVED) {
            throw new IllegalArgumentException("Reserved transaction does not match the request");
        }
        return new PublishAttemptReceipt(
                actorId, request.requestId(), context.lease(), context.binding(), transaction,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Phase.RESERVED
        );
    }

    public PublishAttemptReceipt expectedCaptured(
            PublishDescriptor descriptor,
            WireStatus.HashTriple expectedHashes
    ) {
        requirePhase(Phase.RESERVED);
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(expectedHashes, "expectedHashes");
        requireDescriptorCorrelation(descriptor);
        if (!descriptor.sourceHash().equals(expectedHashes.sourceHash())
                || !descriptor.runtimeHash().equals(expectedHashes.runtimeHash())
                || !descriptor.runtimeContainerHash().equals(expectedHashes.runtimeContainerHash())) {
            throw new IllegalArgumentException("Expected descriptor and hash triple do not match");
        }
        return copy(
                Optional.of(descriptor), Optional.empty(), Optional.of(expectedHashes),
                Optional.empty(), Phase.EXPECTED_CAPTURED
        );
    }

    public PublishAttemptReceipt prepared(PublishTransaction transaction) {
        requirePhase(Phase.EXPECTED_CAPTURED);
        Objects.requireNonNull(transaction, "transaction");
        PublishDescriptor expected = expectedDescriptor.orElseThrow();
        if (!transaction.target().equals(reserved.target())
                || !transaction.transactionDirectory().equals(reserved.transactionDirectory())
                || !transaction.expiresAt().equals(reserved.expiresAt())
                || !transaction.descriptor().equals(expected)
                || transaction.state()
                != com.ptcrys.fpsmatch.core.minimap.storage.PublishState.PREPARED) {
            throw new IllegalArgumentException("Prepared transaction does not match the receipt");
        }
        return copy(expectedDescriptor, Optional.of(transaction), hashes,
                Optional.empty(), Phase.PREPARED);
    }

    public PublishAttemptReceipt commitAttempted() {
        requirePhase(Phase.PREPARED);
        return copy(expectedDescriptor, prepared, hashes, Optional.empty(), Phase.COMMIT_ATTEMPTED);
    }

    public PublishAttemptReceipt resultReady(PublishWireMessage.PublishResult publishResult) {
        requirePhase(Phase.COMMIT_ATTEMPTED);
        Objects.requireNonNull(publishResult, "publishResult");
        if (!publishResult.requestId().equals(requestId)
                || !publishResult.lease().equals(lease)
                || !publishResult.binding().equals(binding)
                || !publishResult.publishToken().equals(reserved.publishToken())
                || publishResult.publishRevision() != reserved.publishRevision()) {
            throw new IllegalArgumentException("Publish result does not match the receipt");
        }
        if (publishResult.outcome() == WireStatus.PublishOutcome.ABORTED) {
            throw new IllegalArgumentException("Post-commit receipt cannot record ABORTED");
        }
        if (publishResult.outcome() == WireStatus.PublishOutcome.COMMITTED
                && !publishResult.hashes().equals(hashes)) {
            throw new IllegalArgumentException("Committed result hashes do not match the receipt");
        }
        if (publishResult.outcome() == WireStatus.PublishOutcome.STATUS_UNKNOWN
                && (publishResult.hashes().isPresent()
                || publishResult.error().map(WireStatus.ErrorInfo::retryDisposition)
                .filter(value -> value == WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS)
                .isEmpty())) {
            throw new IllegalArgumentException("Unknown result is not queryable");
        }
        return copy(expectedDescriptor, prepared, hashes,
                Optional.of(publishResult), Phase.RESULT_READY);
    }

    public PublishAttemptReceipt resultRecorded() {
        requirePhase(Phase.RESULT_READY);
        return copy(expectedDescriptor, prepared, hashes, result, Phase.RESULT_RECORDED);
    }

    public UUID actorId() {
        return actorId;
    }

    public UUID requestId() {
        return requestId;
    }

    public WireIdentity.ScopeLease lease() {
        return lease;
    }

    public WireIdentity.DocumentBinding binding() {
        return binding;
    }

    public PublishTransaction reservedTransaction() {
        return reserved;
    }

    public Optional<PublishDescriptor> expectedDescriptor() {
        return expectedDescriptor;
    }

    public Optional<PublishTransaction> preparedTransaction() {
        return prepared;
    }

    public Optional<WireStatus.HashTriple> hashes() {
        return hashes;
    }

    public Optional<PublishWireMessage.PublishResult> result() {
        return result;
    }

    public Phase phase() {
        return phase;
    }

    /** Returns a fresh copy suitable for a durable BO receipt generation. */
    public byte[] canonicalBytes() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("phase", phase.name());
        root.addProperty("actorId", actorId.toString());
        root.addProperty("requestId", requestId.toString());
        JsonObject leaseJson = new JsonObject();
        leaseJson.addProperty("scope", lease.scope().name());
        leaseJson.addProperty("scopeEpoch", Long.toString(lease.scopeEpoch()));
        leaseJson.addProperty("runtimeGeneration", Long.toString(lease.runtimeGeneration()));
        root.add("lease", leaseJson);
        root.add("binding", bindingJson(binding));
        root.add("reserved", transactionJson(reserved));
        expectedDescriptor.ifPresent(value -> root.add("expectedDescriptor", descriptorJson(value)));
        prepared.ifPresent(value -> root.add("prepared", transactionJson(value)));
        hashes.ifPresent(value -> root.add("hashes", hashesJson(value)));
        result.ifPresent(value -> root.add("result", resultJson(value)));
        return JcsCanonicalizer.canonicalize(root).clone();
    }

    private PublishAttemptReceipt copy(
            Optional<PublishDescriptor> nextExpected,
            Optional<PublishTransaction> nextPrepared,
            Optional<WireStatus.HashTriple> nextHashes,
            Optional<PublishWireMessage.PublishResult> nextResult,
            Phase nextPhase
    ) {
        return new PublishAttemptReceipt(
                actorId, requestId, lease, binding, reserved, nextExpected,
                nextPrepared, nextHashes, nextResult, nextPhase
        );
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Receipt phase must be " + expected);
        }
    }

    private void requireDescriptorCorrelation(PublishDescriptor descriptor) {
        PublishDescriptor original = reserved.descriptor();
        if (!descriptor.publishToken().equals(original.publishToken())
                || descriptor.baseRevision() != original.baseRevision()
                || descriptor.publishRevision() != original.publishRevision()
                || descriptor.expiresAtEpochMillis() != original.expiresAtEpochMillis()) {
            throw new IllegalArgumentException("Expected descriptor does not match the reservation");
        }
    }

    private static JsonObject bindingJson(WireIdentity.DocumentBinding value) {
        JsonObject json = new JsonObject();
        json.addProperty("gameType", value.target().mapKey().gameType());
        json.addProperty("mapName", value.target().mapKey().mapName());
        json.addProperty("dimension", value.target().dimension().toString());
        json.addProperty("documentId", value.documentId().toString());
        return json;
    }

    private static JsonObject transactionJson(PublishTransaction value) {
        JsonObject json = new JsonObject();
        json.add("target", bindingJson(new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(value.mapKey(), value.dimension()), value.documentId()
        )));
        json.add("descriptor", descriptorJson(value.descriptor()));
        json.addProperty("descriptorChecksum", value.descriptor().descriptorChecksum().value());
        json.addProperty("transactionDirectory", value.transactionDirectory().toString());
        json.addProperty("expiresAtEpochMillis", Long.toString(value.expiresAt().toEpochMilli()));
        json.addProperty("state", value.state().name());
        return json;
    }

    private static JsonObject descriptorJson(PublishDescriptor value) {
        JsonObject json = new JsonObject();
        json.addProperty("publishToken", value.publishToken());
        json.addProperty("baseRevision", Long.toString(value.baseRevision()));
        json.addProperty("publishRevision", Long.toString(value.publishRevision()));
        json.addProperty("expiresAtEpochMillis", Long.toString(value.expiresAtEpochMillis()));
        json.addProperty("sourceHash", value.sourceHash().value());
        json.addProperty("runtimeHash", value.runtimeHash().value());
        json.addProperty("runtimeContainerHash", value.runtimeContainerHash().value());
        json.addProperty("descriptorChecksum", value.descriptorChecksum().value());
        return json;
    }

    private static JsonObject hashesJson(WireStatus.HashTriple value) {
        JsonObject json = new JsonObject();
        json.addProperty("sourceHash", value.sourceHash().value());
        json.addProperty("runtimeHash", value.runtimeHash().value());
        json.addProperty("runtimeContainerHash", value.runtimeContainerHash().value());
        return json;
    }

    private static JsonObject resultJson(PublishWireMessage.PublishResult value) {
        JsonObject json = new JsonObject();
        json.addProperty("requestId", value.requestId().toString());
        json.addProperty("publishToken", value.publishToken());
        json.addProperty("publishRevision", Long.toString(value.publishRevision()));
        json.addProperty("outcome", value.outcome().name());
        value.hashes().ifPresent(hashes -> json.add("hashes", hashesJson(hashes)));
        value.error().ifPresent(error -> {
            JsonObject errorJson = new JsonObject();
            errorJson.addProperty("errorCode", error.errorCode());
            errorJson.addProperty("retryDisposition", error.retryDisposition().name());
            errorJson.addProperty("detail", error.detail());
            json.add("error", errorJson);
        });
        return json;
    }
}
