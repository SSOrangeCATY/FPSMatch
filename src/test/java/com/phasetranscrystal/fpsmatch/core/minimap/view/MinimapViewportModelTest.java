package com.phasetranscrystal.fpsmatch.core.minimap.view;

import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineTransform2D;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldPoint2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapViewportModelTest {
    private static final AffineTransform2D IDENTITY = new AffineTransform2D(1, 0, 0, 0, 1, 0);

    @Test
    void fixedNorthAndPlayerUpProjectPoseAndYaw() {
        ViewportCamera north = ViewportCamera.fixedNorth(0, 0, 1.0, 100, 100);
        ProjectedPose pose = north.project(IDENTITY, 10, 0, 20, 90f);
        assertEquals(10.0, pose.canvasX(), 1e-9);
        assertEquals(20.0, pose.canvasY(), 1e-9);
        assertEquals(90f, pose.displayYawDegrees(), 1e-3);

        ViewportCamera playerUp = ViewportCamera.playerUp(0, 0, 1.0, 100, 100, 90f);
        ProjectedPose rotated = playerUp.project(IDENTITY, 10, 0, 0, 0f);
        // player yaw 90 => world +X becomes canvas +Y after map rotation -90
        assertEquals(0.0, rotated.canvasX(), 1e-6);
        assertEquals(10.0, rotated.canvasY(), 1e-6);
    }

    @Test
    void squareAndCircleClippingAndPanOverscroll() {
        ViewportCamera camera = ViewportCamera.fixedNorth(0, 0, 1.0, 100, 100);
        assertTrue(camera.isInsideClip(ShapeMode.SQUARE, 49, 49));
        assertFalse(camera.isInsideClip(ShapeMode.SQUARE, 51, 0));
        assertTrue(camera.isInsideClip(ShapeMode.CIRCLE, 0, 49));
        assertFalse(camera.isInsideClip(ShapeMode.CIRCLE, 40, 40));

        ViewportCamera panned = camera.panBy(1000, 0);
        // 10% overscroll of half-width (50) => max abs pan 55
        assertEquals(55.0, panned.panX(), 1e-9);
    }

    @Test
    void cursorAnchoredZoomAndFit() {
        ViewportCamera camera = ViewportCamera.fixedNorth(0, 0, 1.0, 200, 200);
        ViewportCamera zoomed = camera.zoomAt(2.0, 50, 0);
        // cursor world stays under cursor after zoom
        assertEquals(50.0, zoomed.screenToWorldX(50), 1e-6);
        ViewportCamera fitted = ViewportCamera.fit(IDENTITY, new WorldPoint2D(-10, -5), new WorldPoint2D(10, 5), 200, 100, 0.1);
        assertTrue(fitted.zoom() > 0);
    }

    @Test
    void hudAutoFloorIsIsolatedFromTacticalManualFloor() {
        FloorViewState hud = FloorViewState.automatic("ground");
        FloorViewState tactical = FloorViewState.manual("upper", 20);
        tactical = tactical.tickTimeout(1);
        assertEquals("ground", hud.effectiveFloorId().orElseThrow());
        assertEquals("upper", tactical.effectiveFloorId().orElseThrow());
        FloorViewState timedOut = tactical.tickTimeout(25);
        assertTrue(timedOut.mode() == FloorViewMode.AUTOMATIC || timedOut.manualTimeoutRemaining() == 0);
        // hud unchanged
        assertEquals("ground", hud.effectiveFloorId().orElseThrow());
    }

    @Test
    void labelCollisionUsesStableOrderAndFiltersMarkersByFloorStyle() {
        List<LabelCandidate> labels = List.of(
                new LabelCandidate("b", 10, 10, 20, 8),
                new LabelCandidate("a", 12, 10, 20, 8),
                new LabelCandidate("c", 100, 100, 20, 8)
        );
        List<String> kept = LabelCollisionResolver.resolve(labels).stream().map(LabelCandidate::id).toList();
        assertEquals(List.of("a", "c"), kept);

        MarkerSnapshot.Marker same = marker("fpsmatch:m1", "ground");
        MarkerSnapshot.Marker adjacent = marker("fpsmatch:m2", "upper");
        MarkerSnapshot.Marker far = marker("fpsmatch:m3", "roof");
        List<MarkerRenderIntent> intents = MarkerFloorFilter.filter(
                "ground",
                List.of(same, adjacent, far),
                true
        );
        assertEquals(2, intents.size());
        assertEquals(AdjacentFloorStyle.NONE, intents.get(0).adjacentStyle());
        assertEquals(AdjacentFloorStyle.ABOVE, intents.get(1).adjacentStyle());
    }

    @Test
    void labelCollisionKeepsHigherPriorityBeforeStableId() {
        List<LabelCandidate> labels = List.of(
                new LabelCandidate("a", 10, 10, 20, 8, 1),
                new LabelCandidate("z", 12, 10, 20, 8, 100),
                new LabelCandidate("b", 100, 100, 20, 8, 1)
        );

        List<String> kept = LabelCollisionResolver.resolve(labels).stream()
                .map(LabelCandidate::id)
                .toList();

        assertEquals(List.of("z", "b"), kept);
    }

    private static MarkerSnapshot.Marker marker(String id, String floor) {
        return new MarkerSnapshot.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse("fpsmatch:type/player"),
                NamespacedId.parse("fpsmatch:style/default"),
                0, 0, 0, 0f, 0L, Optional.empty(), Optional.of(floor)
        );
    }
}
