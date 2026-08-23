package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.core.minimap.view.ShapeMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecordingMinimapDrawBackend implements MinimapDrawBackend {
    private final List<MinimapFrame> submitted = new ArrayList<>();

    @Override
    public void submit(MinimapFrame frame) {
        submitted.add(Objects.requireNonNull(frame, "frame"));
    }

    public List<MinimapFrame> submitted() {
        return List.copyOf(submitted);
    }

    public ShapeMode lastShape() {
        if (submitted.isEmpty()) {
            throw new IllegalStateException("no frames submitted");
        }
        return submitted.get(submitted.size() - 1).shape();
    }
}