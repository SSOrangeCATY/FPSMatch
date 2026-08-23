package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

public record ContainerLimits(
        int maxEntries,
        long maxEntryBytes,
        long maxExpandedBytes,
        long maxCompressionRatio
) {
    private static final long CANONICAL_COMPRESSION_RATIO = 1;
    private static final long LOCAL_AND_CENTRAL_FIXED_BYTES = 30L + 46L;
    private static final long END_OF_CENTRAL_DIRECTORY_BYTES = 22L;

    public ContainerLimits {
        if (maxEntries <= 0 || maxEntries > MinimapHardLimits.MAX_ZIP_ENTRIES) {
            throw new IllegalArgumentException("ZIP entry limit is outside the hard limit");
        }
        if (maxEntryBytes <= 0 || maxEntryBytes > MinimapHardLimits.MAX_ZIP_ENTRY_BYTES) {
            throw new IllegalArgumentException("ZIP entry byte limit is outside the hard limit");
        }
        if (maxExpandedBytes <= 0 || maxExpandedBytes > MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES) {
            throw new IllegalArgumentException("ZIP expanded byte limit is outside the hard limit");
        }
        if (maxCompressionRatio <= 0) {
            throw new IllegalArgumentException("ZIP compression ratio limit must be positive");
        }
    }

    public static ContainerLimits sourceHardLimits() {
        return new ContainerLimits(
                MinimapHardLimits.MAX_ZIP_ENTRIES,
                MinimapHardLimits.MAX_ZIP_ENTRY_BYTES,
                MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES,
                CANONICAL_COMPRESSION_RATIO
        );
    }

    public static ContainerLimits runtimeHardLimits() {
        return new ContainerLimits(
                MinimapHardLimits.MAX_ZIP_ENTRIES,
                MinimapHardLimits.MAX_ZIP_ENTRY_BYTES,
                MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES,
                CANONICAL_COMPRESSION_RATIO
        );
    }

    public long maxCanonicalContainerBytes() {
        long pathBytes = 2L * MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES;
        long entryOverhead = Math.addExact(LOCAL_AND_CENTRAL_FIXED_BYTES, pathBytes);
        return Math.addExact(
                maxExpandedBytes,
                Math.addExact(
                        Math.multiplyExact(maxEntries, entryOverhead),
                        END_OF_CENTRAL_DIRECTORY_BYTES
                )
        );
    }
}
