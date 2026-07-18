package com.phasetranscrystal.fpsmatch.core.minimap.contract;

import java.time.Duration;

public final class MinimapHardLimits {
    public static final int MAX_GAME_TYPE_UTF8_BYTES = 64;
    public static final int MAX_MAP_NAME_UTF8_BYTES = 256;
    public static final int MAX_NAMESPACE_UTF8_BYTES = 64;
    public static final int MAX_NAMESPACED_PATH_UTF8_BYTES = 256;
    public static final int MAX_INTERNAL_SLUG_UTF8_BYTES = 64;
    public static final int MAX_FORMAT_VERSION_UTF8_BYTES = 21;
    public static final int MAX_ENTRY_PATH_UTF8_BYTES = 512;
    public static final int MAX_WIRE_STRING_UTF8_BYTES = 32 * 1024;
    public static final int MAX_BUILTIN_BINDING_BYTES = 64 * 1024;

    public static final int MAX_CANVAS_EDGE = 16_384;
    public static final int MAX_FLOORS = 32;
    public static final int MAX_SOURCE_LAYERS = 256;
    public static final int MAX_REGIONS = 8_192;
    public static final int MAX_VECTOR_VERTICES = 262_144;
    public static final int MAX_ZIP_ENTRIES = 32_768;
    public static final long MAX_RUNTIME_MANIFEST_BYTES = 4L * 1024 * 1024;
    public static final long MAX_SOURCE_MANIFEST_BYTES = 4L * 1024 * 1024;
    public static final long MAX_JSON_ENTRY_BYTES = 64L * 1024 * 1024;
    public static final long MAX_ZIP_ENTRY_BYTES = 128L * 1024 * 1024;
    public static final int MAX_TILE_EDGE = 1_024;
    public static final long MAX_CANONICAL_PNG_COMPRESSED_BYTES = 128L * 1024 * 1024;
    public static final long MAX_SOURCE_EXPANDED_BYTES = 1L * 1024 * 1024 * 1024;
    public static final long MAX_RUNTIME_EXPANDED_BYTES = 512L * 1024 * 1024;
    public static final int MAX_WIRE_FRAGMENT_BYTES = 256 * 1024;
    public static final int MAX_WIRE_FRAGMENT_METADATA_BYTES = 32 * 1024;
    public static final int MAX_WIRE_FRAME_BYTES = 320 * 1024;
    public static final int MAX_FORGE_C2S_SEGMENT_BYTES = 30 * 1024;
    public static final int MAX_REASSEMBLY_FRAMES_PER_CONNECTION = 4;
    public static final int MAX_REASSEMBLY_BYTES_PER_CONNECTION = 1_280 * 1024;
    public static final Duration REASSEMBLY_TTL = Duration.ofSeconds(30);
    public static final long MAX_WIRE_TRANSFER_BYTES = 1L * 1024 * 1024 * 1024;
    public static final long MAX_SOURCE_CONTAINER_UPLOAD_BYTES = MAX_WIRE_TRANSFER_BYTES;
    public static final long MAX_RUNTIME_CONTAINER_UPLOAD_BYTES =
            MAX_RUNTIME_EXPANDED_BYTES
                    + (long) MAX_ZIP_ENTRIES
                    * (30L + 46L + 2L * MAX_ENTRY_PATH_UTF8_BYTES)
                    + 22L;
    public static final long MAX_BUILTIN_RUNTIME_CATALOG_BYTES =
            MAX_WIRE_TRANSFER_BYTES;
    public static final long MAX_SOURCE_ENTRY_UPLOAD_BYTES = MAX_ZIP_ENTRY_BYTES;
    public static final int MAX_MARKER_STATE_FIELDS = 64;
    public static final int MAX_MARKER_STATE_BYTES = 4_096;
    public static final int MAX_MARKER_BYTES_VALUE = 3_584;
    public static final int MAX_WIRE_PAGE_COUNT = 4_096;
    public static final int MAX_ENTRY_REQUESTS = 256;
    public static final int MAX_EDITOR_MUTATIONS = 64;
    public static final int MAX_SNAPSHOT_REQUEST_CHANNELS = 32;
    public static final int MAX_SNAPSHOT_SECTIONS_PER_PAGE = 32;
    public static final int MAX_SNAPSHOT_CHANNELS_PER_SECTION = 16;
    public static final long MAX_SNAPSHOT_CHANNEL_BYTES = 128L * 1024 * 1024;
    public static final long MAX_SNAPSHOT_MANIFEST_DECLARED_BYTES =
            512L * 1024 * 1024;
    public static final int MAX_DIRTY_SECTION_RESULTS = 4_096;
    public static final int MAX_REBASE_ITEMS = 128;
    public static final int MAX_PUBLISH_TOKEN_UTF8_BYTES = 128;
    public static final int MAX_ERROR_DETAIL_UTF8_BYTES = 1_024;

    /** @deprecated Use {@link #MAX_WIRE_FRAGMENT_BYTES}. */
    @Deprecated
    public static final int MAX_WIRE_BODY_BYTES = MAX_WIRE_FRAGMENT_BYTES;
    public static final long MAX_DECODED_TILE_BYTES = 4L * MAX_TILE_EDGE * MAX_TILE_EDGE;

    private MinimapHardLimits() {
    }
}
