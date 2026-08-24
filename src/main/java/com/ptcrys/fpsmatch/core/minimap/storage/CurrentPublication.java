package com.ptcrys.fpsmatch.core.minimap.storage;

import java.util.Objects;

public record CurrentPublication(
        CurrentPointer pointer,
        PublishRecord record
) {
    public CurrentPublication {
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(record, "record");
        if (pointer.revision() == 0
                || record.state() != PublishState.COMMITTED
                || record.descriptor().baseRevision() != pointer.expectedBaseRevision()
                || record.descriptor().publishRevision() != pointer.revision()
                || !record.descriptorChecksum().equals(pointer.descriptorChecksum())) {
            throw new IllegalArgumentException("Current publication metadata is inconsistent");
        }
    }

    public PublishTarget target() {
        return record.target();
    }
}
