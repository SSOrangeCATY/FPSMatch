package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapFrame;

import java.util.Objects;
import java.util.Optional;

record MinimapDrawReceipt(MinimapFrame frame, long sequence) {
    MinimapDrawReceipt {
        Objects.requireNonNull(frame, "frame");
        if (sequence <= 0L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
    }

    static boolean confirms(
            Optional<MinimapDrawReceipt> observed,
            long previousSequence,
            MinimapFrame expectedFrame
    ) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(expectedFrame, "expectedFrame");
        MinimapDrawReceipt receipt = observed.orElse(null);
        return receipt != null
                && receipt.sequence() > previousSequence
                && receipt.frame() == expectedFrame;
    }
}
