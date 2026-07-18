package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerTextureDrawPlanTest {
    @Test
    void centersResolvedTextureAtFixedScreenPixelSize() {
        NamespacedId texture = NamespacedId.parse(
                "blockoffensive:textures/minimap/markers/ally.png"
        );
        MarkerPresentationResolver.Resolved resolved =
                new MarkerPresentationResolver.Resolved(
                        texture,
                        DisplayLabel.translation("blockoffensive.minimap.marker.ally"),
                        20.0
                );

        MarkerTextureDrawPlan plan = MarkerTextureDrawPlan.create(
                100.0, 60.0, 45f, 0.55f, Optional.of(resolved)
        ).orElseThrow();

        assertEquals(texture, plan.textureId());
        assertEquals(90, plan.left());
        assertEquals(50, plan.top());
        assertEquals(20, plan.size());
        assertEquals(45f, plan.yawDegrees(), 1e-6);
        assertEquals(0.55f, plan.opacity(), 1e-6);
        assertTrue(MarkerTextureDrawPlan.create(
                100, 60, 0f, 1f, Optional.empty()
        ).isEmpty());
    }
}
