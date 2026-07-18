package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import java.util.Objects;

public record FragmentJob(String entryId, int fragmentIndex, int payloadBytes) {
    public FragmentJob {
        Objects.requireNonNull(entryId, "entryId");
        if (fragmentIndex < 0 || payloadBytes <= 0) {
            throw new IllegalArgumentException("Invalid fragment job");
        }
    }
}