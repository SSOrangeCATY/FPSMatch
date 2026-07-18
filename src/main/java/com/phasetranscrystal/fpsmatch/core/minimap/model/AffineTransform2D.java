package com.phasetranscrystal.fpsmatch.core.minimap.model;

public record AffineTransform2D(
        double m00,
        double m01,
        double translateU,
        double m10,
        double m11,
        double translateV
) {
    private static final double MIN_RECIPROCAL_CONDITION = 1.0e-12;

    public AffineTransform2D {
        if (!Double.isFinite(m00)
                || !Double.isFinite(m01)
                || !Double.isFinite(translateU)
                || !Double.isFinite(m10)
                || !Double.isFinite(m11)
                || !Double.isFinite(translateV)) {
            throw new IllegalArgumentException("Affine transform components must be finite");
        }
        requireWellConditioned(m00, m01, m10, m11);
    }

    public CanvasPoint transform(WorldPoint2D point) {
        return new CanvasPoint(
                m00 * point.x() + m01 * point.z() + translateU,
                m10 * point.x() + m11 * point.z() + translateV
        );
    }

    public Vector2D transformVector(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            throw new IllegalArgumentException("Direction vector must be finite");
        }
        return new Vector2D(
                m00 * worldX + m01 * worldZ,
                m10 * worldX + m11 * worldZ
        );
    }

    public Vector2D northVector() {
        return transformVector(0, -1).normalized();
    }

    public WorldPoint2D inverseTransform(CanvasPoint point) {
        double scale = linearScale(m00, m01, m10, m11);
        double a = m00 / scale;
        double b = m01 / scale;
        double c = m10 / scale;
        double d = m11 / scale;
        double normalizedDeterminant = a * d - b * c;
        double factor = (1.0 / scale) / normalizedDeterminant;
        double deltaU = point.u() - translateU;
        double deltaV = point.v() - translateV;
        return new WorldPoint2D(
                (d * deltaU - b * deltaV) * factor,
                (-c * deltaU + a * deltaV) * factor
        );
    }

    public double determinant() {
        double raw = m00 * m11 - m01 * m10;
        if (Double.isFinite(raw) && raw != 0.0) {
            return raw;
        }
        double scale = linearScale(m00, m01, m10, m11);
        double normalized = (m00 / scale) * (m11 / scale) - (m01 / scale) * (m10 / scale);
        return Math.copySign(Double.isInfinite(raw) ? Double.MAX_VALUE : Double.MIN_VALUE, normalized);
    }

    public boolean isMirrored() {
        double scale = linearScale(m00, m01, m10, m11);
        return (m00 / scale) * (m11 / scale) - (m01 / scale) * (m10 / scale) < 0;
    }

    private static void requireWellConditioned(double m00, double m01, double m10, double m11) {
        double scale = linearScale(m00, m01, m10, m11);
        if (scale == 0.0) {
            throw new IllegalArgumentException("Affine transform must be invertible");
        }
        double a = m00 / scale;
        double b = m01 / scale;
        double c = m10 / scale;
        double d = m11 / scale;
        double determinant = a * d - b * c;
        double frobeniusSquared = a * a + b * b + c * c + d * d;
        double discriminant = Math.sqrt(Math.max(
                0.0,
                frobeniusSquared * frobeniusSquared - 4.0 * determinant * determinant
        ));
        double largestSingularValueSquared = (frobeniusSquared + discriminant) * 0.5;
        double reciprocalCondition = Math.abs(determinant) / largestSingularValueSquared;
        if (!Double.isFinite(reciprocalCondition) || reciprocalCondition < MIN_RECIPROCAL_CONDITION) {
            throw new IllegalArgumentException("Affine transform is singular or numerically unstable");
        }
    }

    private static double linearScale(double m00, double m01, double m10, double m11) {
        return Math.max(Math.max(Math.abs(m00), Math.abs(m01)), Math.max(Math.abs(m10), Math.abs(m11)));
    }
}
