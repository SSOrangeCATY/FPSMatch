package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MarkerPresentationResolver {
    private static final double BASE_SIZE_PIXELS = 16.0;

    private final Supplier<Optional<RuntimeGeneration>> currentGeneration;
    private final Predicate<NamespacedId> resourceExists;
    private final Map<Key, Optional<Resolved>> cache = new LinkedHashMap<>();
    private RuntimeGeneration cachedGeneration;

    public MarkerPresentationResolver(
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            Predicate<NamespacedId> resourceExists
    ) {
        this.currentGeneration = Objects.requireNonNull(
                currentGeneration, "currentGeneration"
        );
        this.resourceExists = Objects.requireNonNull(
                resourceExists, "resourceExists"
        );
    }

    public synchronized Optional<Resolved> resolve(
            NamespacedId typeId,
            NamespacedId styleId
    ) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(styleId, "styleId");
        Optional<RuntimeGeneration> current = currentGeneration.get();
        if (current.isEmpty()) {
            reset();
            return Optional.empty();
        }
        RuntimeGeneration generation = current.orElseThrow();
        if (!generation.equals(cachedGeneration)) {
            cache.clear();
            cachedGeneration = generation;
        }
        Key key = new Key(typeId, styleId);
        return cache.computeIfAbsent(key, ignored -> resolve(generation, key));
    }

    public synchronized void reset() {
        cache.clear();
        cachedGeneration = null;
    }

    private Optional<Resolved> resolve(RuntimeGeneration generation, Key key) {
        Optional<MarkerPresentation> declaration =
                MinimapExtensionRegistry.markerPresentation(
                        generation.mapKey(), key.typeId(), key.styleId()
                );
        if (declaration.isEmpty()) {
            return Optional.empty();
        }
        MarkerPresentation presentation = declaration.orElseThrow();
        if (!resourceExists.test(presentation.textureId())) {
            return Optional.empty();
        }
        return Optional.of(new Resolved(
                presentation.textureId(),
                presentation.label(),
                BASE_SIZE_PIXELS * presentation.scale()
        ));
    }

    private record Key(NamespacedId typeId, NamespacedId styleId) {
    }

    public record Resolved(
            NamespacedId textureId,
            DisplayLabel label,
            double sizePixels
    ) {
        public Resolved {
            Objects.requireNonNull(textureId, "textureId");
            Objects.requireNonNull(label, "label");
            if (!Double.isFinite(sizePixels) || sizePixels <= 0) {
                throw new IllegalArgumentException(
                        "Resolved marker size must be finite and positive"
                );
            }
        }
    }
}
