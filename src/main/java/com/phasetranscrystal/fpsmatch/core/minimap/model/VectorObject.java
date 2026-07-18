package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class VectorObject {
    private final String id;
    private final String floorId;
    private final VectorObjectType type;
    private final List<CanvasPoint> linePoints;
    private final Optional<RectangleGeometry> rectangle;
    private final Optional<PolygonGeometry> polygon;
    private final Optional<CanvasPoint> anchor;
    private final Optional<DisplayLabel> text;
    private final Optional<NamespacedId> styleId;
    private final Optional<FillStyle> fill;
    private final Optional<TextAppearance> textAppearance;
    private final Optional<IconAppearance> icon;
    private final double opacity;

    private VectorObject(
            String id,
            String floorId,
            VectorObjectType type,
            List<CanvasPoint> linePoints,
            Optional<RectangleGeometry> rectangle,
            Optional<PolygonGeometry> polygon,
            Optional<CanvasPoint> anchor,
            Optional<DisplayLabel> text,
            Optional<NamespacedId> styleId,
            Optional<FillStyle> fill,
            Optional<TextAppearance> textAppearance,
            Optional<IconAppearance> icon,
            double opacity
    ) {
        if (!MinimapFormatContract.isInternalSlug(id)
                || !MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Vector and floor IDs must be valid internal slugs");
        }
        this.id = id;
        this.floorId = floorId;
        this.type = Objects.requireNonNull(type, "type");
        this.linePoints = List.copyOf(linePoints);
        this.rectangle = Objects.requireNonNull(rectangle, "rectangle");
        this.polygon = Objects.requireNonNull(polygon, "polygon");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.text = Objects.requireNonNull(text, "text");
        this.styleId = Objects.requireNonNull(styleId, "styleId");
        this.fill = Objects.requireNonNull(fill, "fill");
        this.textAppearance = Objects.requireNonNull(textAppearance, "textAppearance");
        this.icon = Objects.requireNonNull(icon, "icon");
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("Vector opacity must be in [0, 1]");
        }
        this.opacity = opacity;
        validateShape();
    }

    public static VectorObject line(
            String id,
            String floorId,
            List<CanvasPoint> points,
            NamespacedId styleId,
            double opacity
    ) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Line requires at least two points");
        }
        return new VectorObject(
                id, floorId, VectorObjectType.LINE, points,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(styleId), Optional.empty(), Optional.empty(), Optional.empty(),
                opacity
        );
    }

    public static VectorObject rectangle(
            String id,
            String floorId,
            RectangleGeometry geometry,
            NamespacedId styleId,
            Optional<FillStyle> fill,
            double opacity
    ) {
        return new VectorObject(
                id, floorId, VectorObjectType.RECTANGLE, List.of(),
                Optional.of(geometry), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(styleId), Objects.requireNonNull(fill, "fill"),
                Optional.empty(), Optional.empty(), opacity
        );
    }

    public static VectorObject polygon(
            String id,
            String floorId,
            PolygonGeometry geometry,
            NamespacedId styleId,
            Optional<FillStyle> fill,
            double opacity
    ) {
        return new VectorObject(
                id, floorId, VectorObjectType.POLYGON, List.of(),
                Optional.empty(), Optional.of(geometry), Optional.empty(), Optional.empty(),
                Optional.of(styleId), Objects.requireNonNull(fill, "fill"),
                Optional.empty(), Optional.empty(), opacity
        );
    }

    public static VectorObject text(
            String id,
            String floorId,
            CanvasPoint anchor,
            DisplayLabel text,
            NamespacedId styleId,
            TextAppearance appearance,
            double opacity
    ) {
        return new VectorObject(
                id, floorId, VectorObjectType.TEXT, List.of(),
                Optional.empty(), Optional.empty(), Optional.of(anchor), Optional.of(text),
                Optional.of(styleId), Optional.empty(), Optional.of(appearance), Optional.empty(),
                opacity
        );
    }

    public static VectorObject icon(
            String id,
            String floorId,
            CanvasPoint anchor,
            IconAppearance icon,
            double opacity
    ) {
        return new VectorObject(
                id, floorId, VectorObjectType.ICON, List.of(),
                Optional.empty(), Optional.empty(), Optional.of(anchor), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(icon),
                opacity
        );
    }

    public String id() {
        return id;
    }

    public String floorId() {
        return floorId;
    }

    public VectorObjectType type() {
        return type;
    }

    public List<CanvasPoint> linePoints() {
        requireType(VectorObjectType.LINE);
        return linePoints;
    }

    public RectangleGeometry rectangle() {
        requireType(VectorObjectType.RECTANGLE);
        return rectangle.orElseThrow();
    }

    public PolygonGeometry polygon() {
        requireType(VectorObjectType.POLYGON);
        return polygon.orElseThrow();
    }

    public CanvasPoint anchor() {
        if (type != VectorObjectType.TEXT && type != VectorObjectType.ICON) {
            throw new IllegalStateException("Vector object type " + type + " has no anchor");
        }
        return anchor.orElseThrow();
    }

    public DisplayLabel text() {
        requireType(VectorObjectType.TEXT);
        return text.orElseThrow();
    }

    public Optional<NamespacedId> styleId() {
        return styleId;
    }

    public Optional<FillStyle> fill() {
        return fill;
    }

    public Optional<TextAppearance> textAppearance() {
        return textAppearance;
    }

    public Optional<IconAppearance> icon() {
        return icon;
    }

    public double opacity() {
        return opacity;
    }

    public VectorObject withFloorId(String newFloorId) {
        return recreate(id, newFloorId, linePoints, rectangle, polygon, anchor);
    }

    public VectorObject withId(String newId) {
        return recreate(newId, floorId, linePoints, rectangle, polygon, anchor);
    }

    public VectorObject translated(double du, double dv) {
        List<CanvasPoint> movedLine = linePoints.stream()
                .map(point -> new CanvasPoint(point.u() + du, point.v() + dv))
                .toList();
        Optional<RectangleGeometry> movedRect = rectangle.map(geometry -> {
            CanvasRect bounds = geometry.bounds();
            return new RectangleGeometry(new CanvasRect(
                    bounds.minU() + du, bounds.minV() + dv,
                    bounds.maxU() + du, bounds.maxV() + dv
            ));
        });
        Optional<PolygonGeometry> movedPoly = polygon.map(geometry -> new PolygonGeometry(
                geometry.vertices().stream()
                        .map(point -> new CanvasPoint(point.u() + du, point.v() + dv))
                        .toList()
        ));
        Optional<CanvasPoint> movedAnchor = anchor.map(point -> new CanvasPoint(point.u() + du, point.v() + dv));
        return recreate(id, floorId, movedLine, movedRect, movedPoly, movedAnchor);
    }

    private VectorObject recreate(
            String newId,
            String newFloorId,
            List<CanvasPoint> newLinePoints,
            Optional<RectangleGeometry> newRectangle,
            Optional<PolygonGeometry> newPolygon,
            Optional<CanvasPoint> newAnchor
    ) {
        return new VectorObject(
                newId, newFloorId, type, newLinePoints, newRectangle, newPolygon, newAnchor,
                text, styleId, fill, textAppearance, icon, opacity
        );
    }

    private void validateShape() {
        switch (type) {
            case LINE -> {
                if (linePoints.size() < 2) {
                    throw new IllegalArgumentException("Line requires at least two points");
                }
            }
            case RECTANGLE -> {
                if (rectangle.isEmpty()) {
                    throw new IllegalArgumentException("Rectangle geometry is required");
                }
            }
            case POLYGON -> {
                if (polygon.isEmpty()) {
                    throw new IllegalArgumentException("Polygon geometry is required");
                }
            }
            case TEXT -> {
                if (anchor.isEmpty() || text.isEmpty() || textAppearance.isEmpty() || styleId.isEmpty()) {
                    throw new IllegalArgumentException("Text vector requires anchor, text, appearance and style");
                }
            }
            case ICON -> {
                if (anchor.isEmpty() || icon.isEmpty()) {
                    throw new IllegalArgumentException("Icon vector requires anchor and icon appearance");
                }
            }
        }
    }

    private void requireType(VectorObjectType expected) {
        if (type != expected) {
            throw new IllegalStateException("Expected vector type " + expected + " but was " + type);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VectorObject that)) {
            return false;
        }
        return Double.compare(that.opacity, opacity) == 0
                && id.equals(that.id)
                && floorId.equals(that.floorId)
                && type == that.type
                && linePoints.equals(that.linePoints)
                && rectangle.equals(that.rectangle)
                && polygon.equals(that.polygon)
                && anchor.equals(that.anchor)
                && text.equals(that.text)
                && styleId.equals(that.styleId)
                && fill.equals(that.fill)
                && textAppearance.equals(that.textAppearance)
                && icon.equals(that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, floorId, type, linePoints, rectangle, polygon, anchor, text,
                styleId, fill, textAppearance, icon, opacity
        );
    }
}
