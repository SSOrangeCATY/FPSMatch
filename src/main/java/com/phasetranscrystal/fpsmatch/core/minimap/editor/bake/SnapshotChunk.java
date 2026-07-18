package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SnapshotChunk {
    private final UUID snapshotId;
    private final List<WorldSectionSnapshot> sections;
    private final String contentFingerprint;

    private SnapshotChunk(UUID snapshotId, List<WorldSectionSnapshot> sections, String contentFingerprint) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.sections = List.copyOf(sections);
        this.contentFingerprint = Objects.requireNonNull(contentFingerprint, "contentFingerprint");
    }

    public static SnapshotChunk fromSections(UUID snapshotId, List<WorldSectionSnapshot> sections) {
        Objects.requireNonNull(sections, "sections");
        StringBuilder canonical = new StringBuilder();
        for (WorldSectionSnapshot section : sections) {
            canonical.append(section.palette().blockIds().get(0));
            for (byte value : section.blockIndices()) {
                canonical.append('|').append(value);
            }
            for (short value : section.heights()) {
                canonical.append('|').append(value);
            }
            for (byte value : section.light()) {
                canonical.append('|').append(value);
            }
            for (int value : section.biomes()) {
                canonical.append('|').append(value);
            }
            canonical.append(';');
        }
        String fingerprint = Sha256Digest.of(canonical.toString().getBytes(StandardCharsets.UTF_8)).value();
        return new SnapshotChunk(snapshotId, sections, fingerprint);
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public List<WorldSectionSnapshot> sections() {
        // Always allocate a fresh unmodifiable view so callers cannot observe shared mutable aliases.
        return List.copyOf(new ArrayList<>(sections));
    }

    public String contentFingerprint() {
        return contentFingerprint;
    }
}