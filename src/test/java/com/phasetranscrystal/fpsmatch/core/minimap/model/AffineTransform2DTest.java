package com.phasetranscrystal.fpsmatch.core.minimap.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffineTransform2DTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void fitsApprovedFourPointFixtureAndProjectsValidationPoint() {
        AffineFit fit = AffineFit.fit(List.of(
                point(0, 0, 100, 200),
                point(100, 0, 300, 175),
                point(0, 100, 150, 350),
                point(100, 100, 350, 325)
        ), false);

        AffineTransform2D transform = fit.transform();
        assertEquals(2.0, transform.m00(), EPSILON);
        assertEquals(0.5, transform.m01(), EPSILON);
        assertEquals(100.0, transform.translateU(), EPSILON);
        assertEquals(-0.25, transform.m10(), EPSILON);
        assertEquals(1.5, transform.m11(), EPSILON);
        assertEquals(200.0, transform.translateV(), EPSILON);
        assertPoint(190, 220, transform.transform(new WorldPoint2D(40, 20)));
        assertEquals(0.0, fit.rmsResidual(), EPSILON);
        assertEquals(0.0, fit.maxResidual(), EPSILON);
        assertEquals(4, fit.controlPointCount());
        assertTrue(fit.hasResidualDegreesOfFreedom());
        assertFalse(transform.isMirrored());
        assertEquals(3.125, transform.determinant(), EPSILON);

        Vector2D north = transform.northVector();
        assertEquals(1.0, north.length(), EPSILON);
        assertEquals(-0.5 / Math.sqrt(2.5), north.x(), EPSILON);
        assertEquals(-1.5 / Math.sqrt(2.5), north.y(), EPSILON);
    }

    @Test
    void fitsExactlyThreeNonCollinearControlPoints() {
        AffineFit fit = AffineFit.fit(List.of(
                point(0, 0, 10, 20),
                point(10, 0, 30, 20),
                point(0, 10, 10, 50)
        ), false);

        assertPoint(20, 35, fit.transform().transform(new WorldPoint2D(5, 5)));
        assertEquals(0.0, fit.rmsResidual(), EPSILON);
        assertEquals(0.0, fit.maxResidual(), EPSILON);
        assertEquals(3, fit.controlPointCount());
        assertFalse(fit.hasResidualDegreesOfFreedom());
    }

    @Test
    void rejectsTooFewCollinearAndNonFiniteControlPoints() {
        assertThrows(IllegalArgumentException.class, () -> AffineFit.fit(List.of(
                point(0, 0, 0, 0),
                point(1, 0, 1, 0)
        ), false));
        assertThrows(IllegalArgumentException.class, () -> AffineFit.fit(List.of(
                point(0, 0, 0, 0),
                point(1, 1, 1, 1),
                point(2, 2, 2, 2)
        ), false));
        assertThrows(IllegalArgumentException.class, () -> new ControlPoint(
                new WorldPoint2D(Double.NaN, 0), new CanvasPoint(0, 0)
        ));
    }

    @Test
    void rejectsMirroringUnlessExplicitlyAllowed() {
        List<ControlPoint> mirrored = List.of(
                point(0, 0, 0, 0),
                point(10, 0, -10, 0),
                point(0, 10, 0, 10)
        );

        assertThrows(IllegalArgumentException.class, () -> AffineFit.fit(mirrored, false));
        AffineTransform2D transform = AffineFit.fit(mirrored, true).transform();
        assertTrue(transform.isMirrored());
        assertEquals(-1.0, transform.determinant(), EPSILON);
        assertPoint(-4, 7, transform.transform(new WorldPoint2D(4, 7)));
    }

    @Test
    void rejectsNonFiniteAndRelativelySingularLinearPartsWithoutScaleUnderflow() {
        assertThrows(IllegalArgumentException.class, () -> new AffineTransform2D(
                Double.MAX_VALUE, Double.MAX_VALUE, 0,
                Double.MAX_VALUE, Double.MAX_VALUE, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new AffineTransform2D(
                1, 1, 0,
                1, 1 + 1.0e-14, 0
        ));

        AffineTransform2D tiny = assertDoesNotThrow(() -> new AffineTransform2D(
                1.0e-200, 0, 0,
                0, 1.0e-200, 0
        ));
        assertFalse(tiny.isMirrored());
        assertPoint(1, 1, tiny.transform(new WorldPoint2D(1.0e200, 1.0e200)));
    }

    @Test
    void inverseProjectionRoundTripsCanvasAndWorldCoordinates() {
        AffineTransform2D transform = AffineFit.fit(List.of(
                point(0, 0, 100, 200),
                point(100, 0, 300, 175),
                point(0, 100, 150, 350),
                point(100, 100, 350, 325)
        ), false).transform();
        WorldPoint2D source = new WorldPoint2D(40, 20);
        CanvasPoint canvas = transform.transform(source);
        WorldPoint2D restored = transform.inverseTransform(canvas);

        assertEquals(source.x(), restored.x(), EPSILON);
        assertEquals(source.z(), restored.z(), EPSILON);
    }

    @Test
    void rejectsNearCollinearCalibrationWithLowRelativeRank() {
        assertThrows(IllegalArgumentException.class, () -> AffineFit.fit(List.of(
                point(0, 0, 0, 0),
                point(1, 1, 1, 1),
                point(2, 2 + 1.0e-11, 2, 2 + 1.0e-11),
                point(3, 3 - 1.0e-11, 3, 3 - 1.0e-11)
        ), false));
    }

    private static ControlPoint point(double x, double z, double u, double v) {
        return new ControlPoint(new WorldPoint2D(x, z), new CanvasPoint(u, v));
    }

    private static void assertPoint(double expectedU, double expectedV, CanvasPoint actual) {
        assertEquals(expectedU, actual.u(), EPSILON);
        assertEquals(expectedV, actual.v(), EPSILON);
    }
}
