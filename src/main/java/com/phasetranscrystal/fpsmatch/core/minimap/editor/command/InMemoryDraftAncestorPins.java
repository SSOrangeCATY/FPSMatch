package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class InMemoryDraftAncestorPins implements DraftAncestorPins {
    private final Set<Sha256> pins = new HashSet<>();

    @Override
    public void pin(Sha256 baseSourceHash) {
        pins.add(Objects.requireNonNull(baseSourceHash, "baseSourceHash"));
    }

    @Override
    public void unpin(Sha256 baseSourceHash) {
        pins.remove(Objects.requireNonNull(baseSourceHash, "baseSourceHash"));
    }

    @Override
    public boolean isPinned(Sha256 baseSourceHash) {
        return pins.contains(Objects.requireNonNull(baseSourceHash, "baseSourceHash"));
    }
}
