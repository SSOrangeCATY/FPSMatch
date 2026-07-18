package com.phasetranscrystal.fpsmatch.core.minimap.editor.vector;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FillStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObject;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class VectorRasterizer {
    private VectorRasterizer() {
    }

    public static VectorDrawPlan plan(List<VectorObject> objects) {
        Objects.requireNonNull(objects, "objects");
        List<VectorDrawPrimitive> primitives = new ArrayList<>();
        for (VectorObject object : objects) {
            switch (object.type()) {
                case LINE -> primitives.add(new VectorDrawPrimitive(
                        VectorDrawPrimitive.Kind.LINE,
                        object.id(),
                        object.linePoints(),
                        Optional.empty(),
                        0xFFFFFFFF,
                        object.opacity()
                ));
                case RECTANGLE -> {
                    CanvasRect bounds = object.rectangle().bounds();
                    List<CanvasPoint> corners = List.of(
                            new CanvasPoint(bounds.minU(), bounds.minV()),
                            new CanvasPoint(bounds.maxU(), bounds.minV()),
                            new CanvasPoint(bounds.maxU(), bounds.maxV()),
                            new CanvasPoint(bounds.minU(), bounds.maxV())
                    );
                    if (object.fill().isPresent()) {
                        primitives.add(new VectorDrawPrimitive(
                                VectorDrawPrimitive.Kind.RECT_FILL,
                                object.id(),
                                corners,
                                Optional.empty(),
                                toRgba(object.fill().orElseThrow().color()),
                                object.opacity() * object.fill().orElseThrow().opacity()
                        ));
                    } else {
                        primitives.add(new VectorDrawPrimitive(
                                VectorDrawPrimitive.Kind.RECT_STROKE,
                                object.id(),
                                corners,
                                Optional.empty(),
                                0xFFFFFFFF,
                                object.opacity()
                        ));
                    }
                }
                case POLYGON -> primitives.add(new VectorDrawPrimitive(
                        VectorDrawPrimitive.Kind.POLYGON_FILL,
                        object.id(),
                        object.polygon().vertices(),
                        Optional.empty(),
                        object.fill().map(fill -> toRgba(fill.color())).orElse(0xFFFFFFFF),
                        object.opacity()
                ));
                case TEXT -> primitives.add(new VectorDrawPrimitive(
                        VectorDrawPrimitive.Kind.TEXT,
                        object.id(),
                        List.of(object.anchor()),
                        Optional.of(object.text()),
                        object.textAppearance().map(appearance -> toRgba(appearance.color())).orElse(0xFFFFFFFF),
                        object.opacity()
                ));
                case ICON -> primitives.add(new VectorDrawPrimitive(
                        VectorDrawPrimitive.Kind.ICON,
                        object.id(),
                        List.of(object.anchor()),
                        Optional.empty(),
                        0xFFFFFFFF,
                        object.opacity()
                ));
            }
        }
        return new VectorDrawPlan(primitives);
    }

    public static int[] rasterizeRectanglePatch(VectorObject rectangle, int width, int height) {
        Objects.requireNonNull(rectangle, "rectangle");
        if (rectangle.type() != VectorObjectType.RECTANGLE) {
            throw new IllegalArgumentException("Expected rectangle vector");
        }
        int[] pixels = new int[width * height];
        CanvasRect bounds = rectangle.rectangle().bounds();
        int rgba = rectangle.fill()
                .map(FillStyle::color)
                .map(VectorRasterizer::toRgba)
                .orElse(0);
        int minX = (int) Math.ceil(bounds.minU());
        int minY = (int) Math.ceil(bounds.minV());
        // half-open style for raster patch: [min,max)
        int maxX = (int) Math.floor(bounds.maxU());
        int maxY = (int) Math.floor(bounds.maxV());
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                if (x >= 0 && y >= 0 && x < width && y < height) {
                    pixels[y * width + x] = rgba;
                }
            }
        }
        return pixels;
    }

    private static int toRgba(RgbaColor color) {
        return Rgba8.of(color.red(), color.green(), color.blue(), color.alpha());
    }
}
