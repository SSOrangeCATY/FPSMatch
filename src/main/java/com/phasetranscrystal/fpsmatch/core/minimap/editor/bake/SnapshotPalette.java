package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import java.util.List;
import java.util.Objects;

public final class SnapshotPalette {
    private final List<String> blockIds;

    public SnapshotPalette(List<String> blockIds) {
        this.blockIds = List.copyOf(Objects.requireNonNull(blockIds, "blockIds"));
        if (this.blockIds.isEmpty()) {
            throw new IllegalArgumentException("Snapshot palette must not be empty");
        }
    }

    public String blockId(int index) {
        return blockIds.get(index);
    }

    public int size() {
        return blockIds.size();
    }

    public List<String> blockIds() {
        return blockIds;
    }
}
