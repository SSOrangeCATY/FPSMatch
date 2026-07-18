package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapGameplayExtension;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerPresentationResolverTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");
    private static final NamespacedId TYPE = NamespacedId.parse("fpsmatch:type/player");
    private static final NamespacedId STYLE = NamespacedId.parse("blockoffensive:style/ally");
    private static final NamespacedId TEXTURE = NamespacedId.parse(
            "blockoffensive:textures/minimap/markers/ally.png"
    );

    @AfterEach
    void clearExtensions() {
        MinimapExtensionRegistry.clearForTests();
    }

    @Test
    void resolvesDeclaredResourcesCachesByGenerationAndResetsNegativeEntries() {
        MinimapExtensionRegistry.register(new MinimapGameplayExtension() {
            @Override public String id() { return "test:markers"; }
            @Override public boolean supports(MapKey mapKey) { return MAP.equals(mapKey); }
            @Override public List<MarkerPresentation> markerPresentations(MapKey mapKey) {
                return List.of(new MarkerPresentation(
                        TYPE, STYLE, TEXTURE,
                        DisplayLabel.translation("blockoffensive.minimap.marker.ally"),
                        1.25
                ));
            }
        });
        AtomicReference<Optional<RuntimeGeneration>> generation =
                new AtomicReference<>(Optional.of(generation(MAP, 1L)));
        AtomicBoolean resourceExists = new AtomicBoolean(true);
        AtomicInteger probes = new AtomicInteger();
        MarkerPresentationResolver resolver = new MarkerPresentationResolver(
                generation::get,
                textureId -> {
                    probes.incrementAndGet();
                    return resourceExists.get() && TEXTURE.equals(textureId);
                }
        );

        MarkerPresentationResolver.Resolved resolved = resolver.resolve(TYPE, STYLE)
                .orElseThrow();
        assertEquals(TEXTURE, resolved.textureId());
        assertEquals(20.0, resolved.sizePixels(), 1e-9);
        assertEquals(1, probes.get());
        assertEquals(resolved, resolver.resolve(TYPE, STYLE).orElseThrow());
        assertEquals(1, probes.get());
        assertTrue(resolver.resolve(TYPE, NamespacedId.parse(
                "blockoffensive:style/unknown"
        )).isEmpty());
        assertEquals(1, probes.get());

        resourceExists.set(false);
        generation.set(Optional.of(generation(MAP, 2L)));
        assertTrue(resolver.resolve(TYPE, STYLE).isEmpty());
        assertEquals(2, probes.get());
        resourceExists.set(true);
        assertTrue(resolver.resolve(TYPE, STYLE).isEmpty());
        assertEquals(2, probes.get());

        resolver.reset();
        assertEquals(TEXTURE, resolver.resolve(TYPE, STYLE).orElseThrow().textureId());
        assertEquals(3, probes.get());
    }

    @Test
    void markerScaleKeepsIconsWithinScreenPixelBounds() {
        assertDoesNotThrow(() -> presentation(0.5));
        assertDoesNotThrow(() -> presentation(2.0));
        assertThrows(IllegalArgumentException.class, () -> presentation(0.49));
        assertThrows(IllegalArgumentException.class, () -> presentation(2.01));
    }

    private static MarkerPresentation presentation(double scale) {
        return new MarkerPresentation(
                TYPE, STYLE, TEXTURE,
                DisplayLabel.translation("blockoffensive.minimap.marker.ally"),
                scale
        );
    }

    private static RuntimeGeneration generation(MapKey mapKey, long localGeneration) {
        return new RuntimeGeneration(
                1L,
                "server-a",
                mapKey,
                NamespacedId.parse("fpsmatch:document/test"),
                1L,
                Sha256Digest.of("runtime".getBytes(StandardCharsets.UTF_8)),
                NamespacedId.parse("minecraft:overworld"),
                localGeneration
        );
    }
}
