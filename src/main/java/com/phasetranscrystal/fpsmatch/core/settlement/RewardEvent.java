package com.phasetranscrystal.fpsmatch.core.settlement;

import java.util.Map;
import java.util.Objects;

public record RewardEvent(String type, int amount, Map<String, String> metadata) {
    public RewardEvent {
        Objects.requireNonNull(type, "type");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
