package com.ptcrys.fpsmatch.common.minimap.server.snapshot;

import com.ptcrys.fpsmatch.common.minimap.server.SectionSnapshotStamp;
import com.ptcrys.fpsmatch.common.minimap.server.WorldSectionKey;
import com.ptcrys.fpsmatch.common.minimap.server.WorldSectionRevisionIndex;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.SectionCoord;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.SnapshotChannelId;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.SnapshotPalette;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldDataSource;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldSectionSnapshot;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * 1.20.1-facing adapter: revision-stamped, server-thread-only section copies for worker-safe bake.
 * Actual block sampling is delegated to {@link SectionAccess} so unit tests stay platform-free.
 */
public final class ServerLevelSnapshotAdapter implements WorldDataSource {
    private final NamespacedId dimension;
    private final WorldSectionRevisionIndex revisionIndex;
    private final SectionAccess sectionAccess;
    private final BooleanSupplier serverThreadCheck;
    private final AtomicLong snapshotIds = new AtomicLong();

    public ServerLevelSnapshotAdapter(
            NamespacedId dimension,
            WorldSectionRevisionIndex revisionIndex,
            SectionAccess sectionAccess,
            BooleanSupplier serverThreadCheck
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.revisionIndex = Objects.requireNonNull(revisionIndex, "revisionIndex");
        this.sectionAccess = Objects.requireNonNull(sectionAccess, "sectionAccess");
        this.serverThreadCheck = Objects.requireNonNull(serverThreadCheck, "serverThreadCheck");
    }

    @Override
    public boolean isSectionLoaded(SectionCoord coord) {
        Objects.requireNonNull(coord, "coord");
        if (!serverThreadCheck.getAsBoolean()) {
            return false;
        }
        return sectionAccess.isSectionLoaded(coord);
    }

    @Override
    public long sectionRevision(SectionCoord coord) {
        Objects.requireNonNull(coord, "coord");
        return revisionIndex.sectionRevision(toKey(coord));
    }

    @Override
    public Optional<WorldSectionSnapshot> copySection(SectionCoord coord, List<SnapshotChannelId> channels) {
        Objects.requireNonNull(coord, "coord");
        Objects.requireNonNull(channels, "channels");
        if (!serverThreadCheck.getAsBoolean()) {
            return Optional.empty();
        }
        if (!sectionAccess.isSectionLoaded(coord)) {
            return Optional.empty();
        }
        WorldSectionKey key = toKey(coord);
        long snapshotId = snapshotIds.incrementAndGet();
        SectionSnapshotStamp begin = revisionIndex.beginSnapshot(snapshotId, key);
        SectionAccess.CopiedSection copied = sectionAccess.copySection(coord, channels);
        SectionSnapshotStamp finished = revisionIndex.finishSnapshot(begin);
        return Optional.of(new WorldSectionSnapshot(
                coord,
                begin.sectionRevision(),
                true,
                finished.stale(),
                new SnapshotPalette(copied.paletteBlockIds()),
                copied.blockIndices(),
                copied.heights(),
                copied.light(),
                copied.biomes()
        ));
    }

    private WorldSectionKey toKey(SectionCoord coord) {
        return new WorldSectionKey(dimension, coord.sectionX(), coord.sectionY(), coord.sectionZ());
    }
}