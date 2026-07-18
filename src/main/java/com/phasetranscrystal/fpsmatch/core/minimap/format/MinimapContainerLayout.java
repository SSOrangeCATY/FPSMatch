package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinimapContainerLayout {
    private static final String DECIMAL = "(?:0|[1-9][0-9]*)";
    private static final Pattern SOURCE_LAYER_TILE = Pattern.compile(
            "floors/([^/]+)/layers/([^/]+)/(tiles|mask)/(" + DECIMAL + ")_(" + DECIMAL + ")\\.png"
    );
    private static final Pattern SOURCE_ASSET_TILE = Pattern.compile(
            "assets/images/([^/]+)/tiles/(" + DECIMAL + ")_(" + DECIMAL + ")\\.png"
    );
    private static final Pattern RUNTIME_FLOOR_TILE = Pattern.compile(
            "floors/([^/]+)/tiles/(" + DECIMAL + ")/(" + DECIMAL + ")_(" + DECIMAL + ")\\.png"
    );

    public static final ContainerPath SOURCE_MANIFEST = path("manifest.json");
    public static final ContainerPath SOURCE_DOCUMENT = path("document.json");
    public static final ContainerPath SOURCE_REGIONS = path("regions.json");
    public static final ContainerPath CONNECTIONS = path("connections.json");
    public static final ContainerPath SOURCE_STYLES = path("styles.json");
    public static final ContainerPath SOURCE_GENERATORS = path("generators.json");
    public static final ContainerPath SOURCE_VECTORS = path("vectors.json");
    public static final ContainerPath RUNTIME_MANIFEST = path("runtime-manifest.json");
    public static final ContainerPath RUNTIME_REGIONS = path("regions-runtime.json");
    public static final ContainerPath RUNTIME_STYLES = path("styles-runtime.json");
    public static final ContainerPath THUMBNAIL = path("thumbnail.png");

    private static final Map<ContainerPath, SourceEntryKind> SOURCE_FIXED_KINDS = Map.ofEntries(
            Map.entry(SOURCE_MANIFEST, SourceEntryKind.MANIFEST),
            Map.entry(SOURCE_DOCUMENT, SourceEntryKind.DOCUMENT),
            Map.entry(SOURCE_REGIONS, SourceEntryKind.REGIONS),
            Map.entry(CONNECTIONS, SourceEntryKind.CONNECTIONS),
            Map.entry(SOURCE_STYLES, SourceEntryKind.STYLES),
            Map.entry(SOURCE_GENERATORS, SourceEntryKind.GENERATORS),
            Map.entry(SOURCE_VECTORS, SourceEntryKind.VECTORS),
            Map.entry(THUMBNAIL, SourceEntryKind.THUMBNAIL)
    );
    private static final Map<ContainerPath, RuntimeEntryKind> RUNTIME_FIXED_KINDS = Map.ofEntries(
            Map.entry(RUNTIME_MANIFEST, RuntimeEntryKind.MANIFEST),
            Map.entry(RUNTIME_REGIONS, RuntimeEntryKind.REGIONS),
            Map.entry(CONNECTIONS, RuntimeEntryKind.CONNECTIONS),
            Map.entry(RUNTIME_STYLES, RuntimeEntryKind.STYLES),
            Map.entry(THUMBNAIL, RuntimeEntryKind.THUMBNAIL)
    );

    public static final Set<ContainerPath> SOURCE_FIXED_PATHS = Set.copyOf(SOURCE_FIXED_KINDS.keySet());
    public static final Set<ContainerPath> RUNTIME_FIXED_PATHS = Set.copyOf(RUNTIME_FIXED_KINDS.keySet());
    public static final Set<ContainerPath> SOURCE_JSON_PATHS = Set.of(
            SOURCE_MANIFEST,
            SOURCE_DOCUMENT,
            SOURCE_REGIONS,
            CONNECTIONS,
            SOURCE_STYLES,
            SOURCE_GENERATORS,
            SOURCE_VECTORS
    );
    public static final Set<ContainerPath> RUNTIME_JSON_PATHS = Set.of(
            RUNTIME_MANIFEST,
            RUNTIME_REGIONS,
            CONNECTIONS,
            RUNTIME_STYLES
    );

    private MinimapContainerLayout() {
    }

    public static Optional<SourceEntryKind> classifySource(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        SourceEntryKind fixedKind = SOURCE_FIXED_KINDS.get(path);
        if (fixedKind != null) {
            return Optional.of(fixedKind);
        }

        Matcher layerTile = SOURCE_LAYER_TILE.matcher(path.value());
        if (layerTile.matches()
                && isInternalSlug(layerTile.group(1))
                && isInternalSlug(layerTile.group(2))) {
            return Optional.of(layerTile.group(3).equals("tiles")
                    ? SourceEntryKind.LAYER_TILE
                    : SourceEntryKind.LAYER_MASK);
        }

        Matcher assetTile = SOURCE_ASSET_TILE.matcher(path.value());
        if (assetTile.matches() && isInternalSlug(assetTile.group(1))) {
            return Optional.of(SourceEntryKind.ASSET_TILE);
        }
        return Optional.empty();
    }

    public static Optional<SourceTileAddress> parseSourceTile(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        Matcher layerTile = SOURCE_LAYER_TILE.matcher(path.value());
        if (layerTile.matches()
                && isInternalSlug(layerTile.group(1))
                && isInternalSlug(layerTile.group(2))) {
            return parseCoordinates(layerTile.group(4), layerTile.group(5))
                    .map(coordinates -> new SourceTileAddress(
                            layerTile.group(3).equals("tiles")
                                    ? SourceEntryKind.LAYER_TILE : SourceEntryKind.LAYER_MASK,
                            layerTile.group(1), layerTile.group(2),
                            coordinates.x(), coordinates.y()
                    ));
        }
        Matcher assetTile = SOURCE_ASSET_TILE.matcher(path.value());
        if (assetTile.matches() && isInternalSlug(assetTile.group(1))) {
            return parseCoordinates(assetTile.group(2), assetTile.group(3))
                    .map(coordinates -> new SourceTileAddress(
                            SourceEntryKind.ASSET_TILE, null, assetTile.group(1),
                            coordinates.x(), coordinates.y()
                    ));
        }
        return Optional.empty();
    }

    public static Optional<RuntimeEntryKind> classifyRuntime(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        RuntimeEntryKind fixedKind = RUNTIME_FIXED_KINDS.get(path);
        if (fixedKind != null) {
            return Optional.of(fixedKind);
        }

        Matcher floorTile = RUNTIME_FLOOR_TILE.matcher(path.value());
        if (floorTile.matches() && isInternalSlug(floorTile.group(1))) {
            return Optional.of(RuntimeEntryKind.FLOOR_TILE);
        }
        return Optional.empty();
    }

    public static Optional<RuntimeTileAddress> parseRuntimeTile(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        Matcher floorTile = RUNTIME_FLOOR_TILE.matcher(path.value());
        if (!floorTile.matches() || !isInternalSlug(floorTile.group(1))) {
            return Optional.empty();
        }
        try {
            int zoom = parseUnsigned(floorTile.group(2));
            return parseCoordinates(floorTile.group(3), floorTile.group(4))
                    .map(coordinates -> new RuntimeTileAddress(
                            floorTile.group(1), zoom, coordinates.x(), coordinates.y()
                    ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static boolean isSourcePath(ContainerPath path) {
        return classifySource(path).isPresent();
    }

    public static boolean isRuntimePath(ContainerPath path) {
        return classifyRuntime(path).isPresent();
    }

    public static boolean isSourceJson(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        return SOURCE_JSON_PATHS.contains(path);
    }

    public static boolean isRuntimeJson(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        return RUNTIME_JSON_PATHS.contains(path);
    }

    public static boolean isSourceOnlyPath(ContainerPath path) {
        Objects.requireNonNull(path, "path");
        return isSourcePath(path) && !isRuntimePath(path);
    }

    private static boolean isInternalSlug(String value) {
        return MinimapFormatContract.isInternalSlug(value);
    }

    private static ContainerPath path(String value) {
        return ContainerPath.parse(value);
    }

    private static Optional<Coordinates> parseCoordinates(String x, String y) {
        try {
            return Optional.of(new Coordinates(parseUnsigned(x), parseUnsigned(y)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static int parseUnsigned(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Tile coordinate is too large", exception);
        }
    }

    private record Coordinates(int x, int y) {
    }

    public record SourceTileAddress(
            SourceEntryKind kind,
            String floorId,
            String ownerId,
            int x,
            int y
    ) {
    }

    public record RuntimeTileAddress(String floorId, int zoom, int x, int y) {
    }

    public enum SourceEntryKind {
        MANIFEST,
        DOCUMENT,
        REGIONS,
        CONNECTIONS,
        STYLES,
        GENERATORS,
        VECTORS,
        LAYER_TILE,
        LAYER_MASK,
        ASSET_TILE,
        THUMBNAIL
    }

    public enum RuntimeEntryKind {
        MANIFEST,
        REGIONS,
        CONNECTIONS,
        STYLES,
        FLOOR_TILE,
        THUMBNAIL
    }
}
