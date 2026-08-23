package com.ptcrys.fpsmatch.core.minimap.storage;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record PublishTransaction(
        PublishTarget target,
        PublishDescriptor descriptor,
        Path transactionDirectory,
        Instant expiresAt,
        PublishState state
) {
    public PublishTransaction {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(descriptor, "descriptor");
        transactionDirectory = Objects.requireNonNull(
                transactionDirectory, "transactionDirectory"
        ).toAbsolutePath().normalize();
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(state, "state");
    }

    public PublishTransaction(
            PublishTarget target,
            PublishDescriptor descriptor,
            Path transactionDirectory,
            Instant expiresAt
    ) {
        this(target, descriptor, transactionDirectory, expiresAt, PublishState.RESERVED);
    }

    public MapKey mapKey() {
        return target.mapKey();
    }

    public NamespacedId dimension() {
        return target.dimension();
    }

    public NamespacedId documentId() {
        return target.documentId();
    }

    public String publishToken() {
        return descriptor.publishToken();
    }

    public long baseRevision() {
        return descriptor.baseRevision();
    }

    public long publishRevision() {
        return descriptor.publishRevision();
    }

    public PublishTransaction withDescriptorAndState(
            PublishDescriptor nextDescriptor,
            PublishState nextState
    ) {
        return new PublishTransaction(
                target, nextDescriptor, transactionDirectory, expiresAt, nextState
        );
    }
}
