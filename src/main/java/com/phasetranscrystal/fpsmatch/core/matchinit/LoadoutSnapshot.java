package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LoadoutSnapshot(List<String> weapons, List<String> tools, Map<String, String> metadata) {
    public LoadoutSnapshot {
        weapons = List.copyOf(Objects.requireNonNull(weapons, "weapons"));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
