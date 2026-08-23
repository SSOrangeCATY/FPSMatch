package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;

public record PolygonGeometry(List<CanvasPoint> vertices) implements RegionGeometry {
    private static final double BOUNDARY_EPSILON = 1.0e-9;

    public PolygonGeometry {
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3 || signedDoubleArea(vertices) == 0.0) {
            throw new IllegalArgumentException("Polygon requires at least three non-collinear vertices");
        }
    }

    @Override
    public GeometryType type() {
        return GeometryType.POLYGON;
    }

    @Override
    public boolean contains(CanvasPoint point) {
        boolean inside = false;
        for (int current = 0, previous = vertices.size() - 1; current < vertices.size(); previous = current++) {
            CanvasPoint a = vertices.get(previous);
            CanvasPoint b = vertices.get(current);
            if (isOnSegment(point, a, b)) {
                return true;
            }
            boolean crosses = (a.v() > point.v()) != (b.v() > point.v());
            if (crosses) {
                double intersectU = (b.u() - a.u()) * (point.v() - a.v()) / (b.v() - a.v()) + a.u();
                if (point.u() < intersectU) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static boolean isOnSegment(CanvasPoint point, CanvasPoint a, CanvasPoint b) {
        double cross = (point.u() - a.u()) * (b.v() - a.v())
                - (point.v() - a.v()) * (b.u() - a.u());
        double scale = Math.max(1.0, Math.max(Math.abs(b.u() - a.u()), Math.abs(b.v() - a.v())));
        if (Math.abs(cross) > BOUNDARY_EPSILON * scale) {
            return false;
        }
        return point.u() >= Math.min(a.u(), b.u()) - BOUNDARY_EPSILON
                && point.u() <= Math.max(a.u(), b.u()) + BOUNDARY_EPSILON
                && point.v() >= Math.min(a.v(), b.v()) - BOUNDARY_EPSILON
                && point.v() <= Math.max(a.v(), b.v()) + BOUNDARY_EPSILON;
    }

    private static double signedDoubleArea(List<CanvasPoint> vertices) {
        double area = 0;
        for (int index = 0; index < vertices.size(); index++) {
            CanvasPoint current = vertices.get(index);
            CanvasPoint next = vertices.get((index + 1) % vertices.size());
            area += current.u() * next.v() - next.u() * current.v();
        }
        return area;
    }
}
