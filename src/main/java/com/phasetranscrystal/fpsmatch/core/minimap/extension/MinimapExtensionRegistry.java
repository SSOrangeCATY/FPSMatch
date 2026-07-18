package com.phasetranscrystal.fpsmatch.core.minimap.extension;

import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Global registry for gameplay minimap extensions. FPSMatch core never branches on game types.
 */
public final class MinimapExtensionRegistry {
    private static final Map<String, MinimapGameplayExtension> EXTENSIONS = new LinkedHashMap<>();

    private MinimapExtensionRegistry() {
    }

    public static synchronized void register(MinimapGameplayExtension extension) {
        Objects.requireNonNull(extension, "extension");
        String id = Objects.requireNonNull(extension.id(), "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("extension id cannot be blank");
        }
        if (EXTENSIONS.containsKey(id)) {
            throw new IllegalStateException("duplicate minimap extension id: " + id);
        }
        EXTENSIONS.put(id, extension);
    }

    public static synchronized void clearForTests() {
        EXTENSIONS.clear();
    }

    public static synchronized List<MinimapGameplayExtension> supporting(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        List<MinimapGameplayExtension> result = new ArrayList<>();
        for (MinimapGameplayExtension extension : EXTENSIONS.values()) {
            if (extension.supports(mapKey)) {
                result.add(extension);
            }
        }
        return List.copyOf(result);
    }

    public static synchronized List<MinimapMarkerProvider> markerProviders(MapKey mapKey) {
        List<MinimapMarkerProvider> providers = new ArrayList<>();
        for (MinimapGameplayExtension extension : supporting(mapKey)) {
            providers.addAll(extension.markerProviders(mapKey));
        }
        return List.copyOf(providers);
    }

    public static synchronized List<MarkerPresentation> markerPresentations(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        Map<MarkerPresentationKey, MarkerPresentation> presentations = new LinkedHashMap<>();
        for (MinimapGameplayExtension extension : supporting(mapKey)) {
            List<MarkerPresentation> declared = Objects.requireNonNull(
                    extension.markerPresentations(mapKey), "marker presentations"
            );
            for (MarkerPresentation presentation : declared) {
                Objects.requireNonNull(presentation, "marker presentation");
                MarkerPresentationKey key = new MarkerPresentationKey(
                        presentation.typeId(), presentation.styleId()
                );
                MarkerPresentation previous = presentations.putIfAbsent(key, presentation);
                if (previous != null && !previous.equals(presentation)) {
                    throw new IllegalStateException(
                            "conflicting marker presentation: "
                                    + presentation.typeId() + "|" + presentation.styleId()
                    );
                }
            }
        }
        return presentations.values().stream()
                .sorted(java.util.Comparator
                        .comparing((MarkerPresentation presentation) ->
                                presentation.typeId().toString())
                        .thenComparing(presentation -> presentation.styleId().toString()))
                .toList();
    }

    public static synchronized Optional<MarkerPresentation> markerPresentation(
            MapKey mapKey,
            NamespacedId typeId,
            NamespacedId styleId
    ) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(styleId, "styleId");
        return markerPresentations(mapKey).stream()
                .filter(presentation -> presentation.typeId().equals(typeId)
                        && presentation.styleId().equals(styleId))
                .findFirst();
    }

    public static synchronized Optional<MinimapViewerContext> viewerContext(
            MapKey mapKey,
            UUID actorId
    ) {
        Objects.requireNonNull(actorId, "actorId");
        for (MinimapGameplayExtension extension : supporting(mapKey)) {
            Optional<MinimapViewerContext> context = extension.viewerContext(mapKey, actorId);
            if (context.isPresent()) {
                return context;
            }
        }
        return Optional.empty();
    }

    public static synchronized Optional<MinimapVisibilityPolicy> visibilityPolicy(MapKey mapKey) {
        for (MinimapGameplayExtension extension : supporting(mapKey)) {
            Optional<MinimapVisibilityPolicy> policy = extension.visibilityPolicy(mapKey);
            if (policy.isPresent()) {
                return policy;
            }
        }
        return Optional.empty();
    }

    public static synchronized boolean hasExtension(String id) {
        return EXTENSIONS.containsKey(id);
    }

    public static synchronized int size() {
        return EXTENSIONS.size();
    }

    private record MarkerPresentationKey(
            NamespacedId typeId,
            NamespacedId styleId
    ) {
    }
}
