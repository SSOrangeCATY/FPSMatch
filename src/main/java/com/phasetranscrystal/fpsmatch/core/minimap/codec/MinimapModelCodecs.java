package com.phasetranscrystal.fpsmatch.core.minimap.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BlendMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineTransform2D;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ControlPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionDisplayDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionEndpoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CutoutLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DefaultViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FillStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FloorBackground;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FloorCalibration;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ImportedImageLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.GeometryType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.IconAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.IconStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerCommon;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LineStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MediaType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloorConnection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.PolygonGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Provenance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyleOverride;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RasterPaintLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionVisualLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StrokeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StyleType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObject;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObjectType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldBakeLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldPoint2D;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MinimapModelCodecs {
    public static final Codec<WorldPoint2D> WORLD_POINT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(WorldPoint2D::x),
            Codec.DOUBLE.fieldOf("z").forGetter(WorldPoint2D::z)
    ).apply(instance, WorldPoint2D::new));

    public static final Codec<CanvasPoint> CANVAS_POINT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("u").forGetter(CanvasPoint::u),
            Codec.DOUBLE.fieldOf("v").forGetter(CanvasPoint::v)
    ).apply(instance, CanvasPoint::new));

    public static final Codec<ControlPoint> CONTROL_POINT = RecordCodecBuilder.create(instance -> instance.group(
            WORLD_POINT.fieldOf("world").forGetter(ControlPoint::world),
            CANVAS_POINT.fieldOf("canvas").forGetter(ControlPoint::canvas)
    ).apply(instance, ControlPoint::new));

    public static final Codec<AffineTransform2D> AFFINE_TRANSFORM = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("m00").forGetter(AffineTransform2D::m00),
            Codec.DOUBLE.fieldOf("m01").forGetter(AffineTransform2D::m01),
            Codec.DOUBLE.fieldOf("translateU").forGetter(AffineTransform2D::translateU),
            Codec.DOUBLE.fieldOf("m10").forGetter(AffineTransform2D::m10),
            Codec.DOUBLE.fieldOf("m11").forGetter(AffineTransform2D::m11),
            Codec.DOUBLE.fieldOf("translateV").forGetter(AffineTransform2D::translateV)
    ).apply(instance, AffineTransform2D::new));

    public static final Codec<WorldBounds> WORLD_BOUNDS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("minX").forGetter(WorldBounds::minX),
            Codec.DOUBLE.fieldOf("minZ").forGetter(WorldBounds::minZ),
            Codec.DOUBLE.fieldOf("maxX").forGetter(WorldBounds::maxX),
            Codec.DOUBLE.fieldOf("maxZ").forGetter(WorldBounds::maxZ)
    ).apply(instance, WorldBounds::new));

    public static final Codec<CanvasBounds> CANVAS_BOUNDS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("width").forGetter(CanvasBounds::width),
            Codec.INT.fieldOf("height").forGetter(CanvasBounds::height)
    ).apply(instance, CanvasBounds::new));

    public static final Codec<CanvasRect> CANVAS_RECT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("minU").forGetter(CanvasRect::minU),
            Codec.DOUBLE.fieldOf("minV").forGetter(CanvasRect::minV),
            Codec.DOUBLE.fieldOf("maxU").forGetter(CanvasRect::maxU),
            Codec.DOUBLE.fieldOf("maxV").forGetter(CanvasRect::maxV)
    ).apply(instance, CanvasRect::new));

    public static final Codec<RgbaColor> RGBA_COLOR = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("red").forGetter(RgbaColor::red),
            Codec.INT.fieldOf("green").forGetter(RgbaColor::green),
            Codec.INT.fieldOf("blue").forGetter(RgbaColor::blue),
            Codec.INT.fieldOf("alpha").forGetter(RgbaColor::alpha)
    ).apply(instance, RgbaColor::new));

    public static final Codec<DisplayLabel.Type> DISPLAY_LABEL_TYPE = enumCodec(DisplayLabel.Type.class);
    public static final Codec<DefaultViewMode> DEFAULT_VIEW_MODE = enumCodec(DefaultViewMode.class);
    public static final Codec<BlendMode> BLEND_MODE = enumCodec(BlendMode.class);
    public static final Codec<LayerType> LAYER_TYPE = enumCodec(LayerType.class);
    public static final Codec<GeometryType> GEOMETRY_TYPE = enumCodec(GeometryType.class);
    public static final Codec<StyleType> STYLE_TYPE = enumCodec(StyleType.class);
    public static final Codec<ConnectionType> CONNECTION_TYPE = enumCodec(ConnectionType.class);
    public static final Codec<ConnectionDisplayDirection> CONNECTION_DISPLAY_DIRECTION =
            enumCodec(ConnectionDisplayDirection.class);

    public static final Codec<DisplayLabel> DISPLAY_LABEL = RecordCodecBuilder.create(instance -> instance.group(
            DISPLAY_LABEL_TYPE.fieldOf("type").forGetter(DisplayLabel::type),
            Codec.STRING.fieldOf("value").forGetter(DisplayLabel::value)
    ).apply(instance, DisplayLabel::new));

    public static final Codec<FloorBackground> FLOOR_BACKGROUND = RecordCodecBuilder.create(instance -> instance.group(
            RGBA_COLOR.fieldOf("color").forGetter(FloorBackground::color)
    ).apply(instance, FloorBackground::new));

    public static final Codec<FloorCalibration> FLOOR_CALIBRATION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(CONTROL_POINT).fieldOf("controlPoints").forGetter(FloorCalibration::controlPoints),
            Codec.BOOL.fieldOf("allowMirror").forGetter(FloorCalibration::allowMirror),
            Codec.DOUBLE.fieldOf("maxResidualPixels").forGetter(FloorCalibration::maxResidualPixels)
    ).apply(instance, FloorCalibration::new));

    public static final Codec<MinimapFloor> FLOOR_SELECTION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MinimapFloor::id),
            Codec.DOUBLE.fieldOf("minY").forGetter(MinimapFloor::minY),
            Codec.DOUBLE.fieldOf("maxY").forGetter(MinimapFloor::maxY),
            Codec.INT.fieldOf("autoPriority").forGetter(MinimapFloor::autoPriority),
            Codec.DOUBLE.fieldOf("enterMargin").forGetter(MinimapFloor::enterMargin),
            Codec.DOUBLE.fieldOf("exitMargin").forGetter(MinimapFloor::exitMargin)
    ).apply(instance, MinimapFloor::new));

    public static final Codec<LayerCommon> LAYER_COMMON = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(LayerCommon::id),
            DISPLAY_LABEL.fieldOf("label").forGetter(LayerCommon::label),
            Codec.BOOL.fieldOf("visible").forGetter(LayerCommon::visible),
            Codec.BOOL.fieldOf("locked").forGetter(LayerCommon::locked),
            Codec.DOUBLE.fieldOf("opacity").forGetter(LayerCommon::opacity),
            BLEND_MODE.fieldOf("blendMode").forGetter(LayerCommon::blendMode),
            CANVAS_RECT.optionalFieldOf("clip").forGetter(LayerCommon::clip),
            Codec.BOOL.fieldOf("maskEnabled").forGetter(LayerCommon::maskEnabled)
    ).apply(instance, LayerCommon::new));

    private static final Codec<ImportedImageLayer> IMPORTED_IMAGE_LAYER = RecordCodecBuilder.create(instance -> instance.group(
            LAYER_COMMON.fieldOf("common").forGetter(ImportedImageLayer::common),
            Codec.STRING.fieldOf("assetId").forGetter(ImportedImageLayer::assetId)
    ).apply(instance, ImportedImageLayer::new));

    private static final Codec<WorldBakeLayer> WORLD_BAKE_LAYER = RecordCodecBuilder.create(instance -> instance.group(
            LAYER_COMMON.fieldOf("common").forGetter(WorldBakeLayer::common),
            Codec.STRING.fieldOf("generatorId").forGetter(WorldBakeLayer::generatorId)
    ).apply(instance, WorldBakeLayer::new));

    private static final Codec<RasterPaintLayer> RASTER_PAINT_LAYER = LAYER_COMMON.xmap(
            RasterPaintLayer::new,
            RasterPaintLayer::common
    );

    private static final Codec<VectorLayer> VECTOR_LAYER = RecordCodecBuilder.create(instance -> instance.group(
            LAYER_COMMON.fieldOf("common").forGetter(VectorLayer::common),
            Codec.list(Codec.STRING).fieldOf("vectorIds").forGetter(VectorLayer::vectorIds)
    ).apply(instance, VectorLayer::new));

    private static final Codec<RegionVisualLayer> REGION_VISUAL_LAYER = RecordCodecBuilder.create(instance -> instance.group(
            LAYER_COMMON.fieldOf("common").forGetter(RegionVisualLayer::common),
            Codec.list(Codec.STRING).fieldOf("regionIds").forGetter(RegionVisualLayer::regionIds)
    ).apply(instance, RegionVisualLayer::new));

    private static final Codec<CutoutLayer> CUTOUT_LAYER = LAYER_COMMON.xmap(
            CutoutLayer::new,
            CutoutLayer::common
    );

    public static final Codec<MinimapLayer> LAYER = LAYER_TYPE.dispatch(
            "type",
            MinimapLayer::type,
            type -> MapCodec.assumeMapUnsafe(codecForLayerType(type))
    );

    public static final Codec<SourceFloor> SOURCE_FLOOR = RecordCodecBuilder.create(instance -> instance.group(
            FLOOR_SELECTION.fieldOf("selection").forGetter(SourceFloor::selection),
            DISPLAY_LABEL.fieldOf("label").forGetter(SourceFloor::label),
            CANVAS_RECT.optionalFieldOf("contentBounds").forGetter(SourceFloor::contentBounds),
            FLOOR_BACKGROUND.fieldOf("background").forGetter(SourceFloor::background),
            FLOOR_CALIBRATION.fieldOf("calibration").forGetter(SourceFloor::calibration),
            Codec.list(LAYER).fieldOf("layers").forGetter(SourceFloor::layers)
    ).apply(instance, SourceFloor::new));

    private static final Codec<Map<String, List<String>>> LAYER_ORDER = Codec.unboundedMap(
            Codec.STRING,
            Codec.list(Codec.STRING)
    );

    public static final Codec<SourceDocument> SOURCE_DOCUMENT = RecordCodecBuilder.create(instance -> instance.group(
            WORLD_BOUNDS.fieldOf("worldBounds").forGetter(SourceDocument::worldBounds),
            CANVAS_BOUNDS.fieldOf("canvas").forGetter(SourceDocument::canvas),
            DEFAULT_VIEW_MODE.fieldOf("defaultViewMode").forGetter(SourceDocument::defaultViewMode),
            Codec.list(SOURCE_FLOOR).fieldOf("floors").forGetter(SourceDocument::floors),
            LAYER_ORDER.fieldOf("layerOrder").forGetter(SourceDocument::layerOrder)
    ).apply(instance, SourceDocument::new));

    public static final Codec<MediaType> MEDIA_TYPE = Codec.STRING.flatXmap(
            value -> MediaType.fromValue(value)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown media type")),
            value -> DataResult.success(value.value())
    );

    public static final Codec<SourceEntryDescriptor> SOURCE_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            ContainerPath.CODEC.fieldOf("path").forGetter(SourceEntryDescriptor::path),
            MinimapCodecs.NON_NEGATIVE_LONG.fieldOf("byteLength").forGetter(SourceEntryDescriptor::byteLength),
            MEDIA_TYPE.fieldOf("mediaType").forGetter(SourceEntryDescriptor::mediaType),
            Sha256.codec().fieldOf("sha256").forGetter(SourceEntryDescriptor::sha256)
    ).apply(instance, SourceEntryDescriptor::new));

    public static final Codec<RuntimeEntryDescriptor> RUNTIME_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            ContainerPath.CODEC.fieldOf("path").forGetter(RuntimeEntryDescriptor::path),
            MinimapCodecs.NON_NEGATIVE_LONG.fieldOf("byteLength").forGetter(RuntimeEntryDescriptor::byteLength),
            Sha256.codec().fieldOf("sha256").forGetter(RuntimeEntryDescriptor::sha256)
    ).apply(instance, RuntimeEntryDescriptor::new));

    public static final Codec<Provenance> PROVENANCE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("originDocumentId").forGetter(Provenance::originDocumentId),
            MapKey.codec().fieldOf("originBinding").forGetter(Provenance::originBinding),
            NamespacedId.codec().fieldOf("originDimension").forGetter(Provenance::originDimension),
            MinimapCodecs.NON_NEGATIVE_LONG.fieldOf("originRevision").forGetter(Provenance::originRevision),
            Sha256.codec().fieldOf("originSourceHash").forGetter(Provenance::originSourceHash)
    ).apply(instance, Provenance::new));

    public static final Codec<SourceManifest> SOURCE_MANIFEST = RecordCodecBuilder.create(instance -> instance.group(
            com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion.codec()
                    .fieldOf("formatVersion").forGetter(SourceManifest::formatVersion),
            NamespacedId.codec().fieldOf("documentId").forGetter(SourceManifest::documentId),
            MapKey.codec().fieldOf("binding").forGetter(SourceManifest::binding),
            MinimapCodecs.NON_NEGATIVE_LONG.fieldOf("revision").forGetter(SourceManifest::revision),
            NamespacedId.codec().fieldOf("dimension").forGetter(SourceManifest::dimension),
            PROVENANCE.optionalFieldOf("provenance").forGetter(SourceManifest::provenance),
            Codec.INT.fieldOf("tileEdge").forGetter(SourceManifest::tileEdge),
            Codec.list(SOURCE_ENTRY).fieldOf("entries").forGetter(SourceManifest::entries)
    ).apply(instance, SourceManifest::new));

    public static final Codec<CompilerProfile> COMPILER_PROFILE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("id").forGetter(CompilerProfile::id),
            com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion.codec()
                    .fieldOf("version").forGetter(CompilerProfile::version)
    ).apply(instance, CompilerProfile::new));

    public static final Codec<RuntimeFloor> RUNTIME_FLOOR = RecordCodecBuilder.create(instance -> instance.group(
            FLOOR_SELECTION.fieldOf("selection").forGetter(RuntimeFloor::selection),
            DISPLAY_LABEL.fieldOf("label").forGetter(RuntimeFloor::label),
            CANVAS_RECT.optionalFieldOf("contentBounds").forGetter(RuntimeFloor::contentBounds),
            AFFINE_TRANSFORM.fieldOf("worldToCanvas").forGetter(RuntimeFloor::worldToCanvas),
            Codec.INT.fieldOf("zoomLevels").forGetter(RuntimeFloor::zoomLevels)
    ).apply(instance, RuntimeFloor::new));

    public static final Codec<RuntimeManifest> RUNTIME_MANIFEST = RecordCodecBuilder.create(instance -> instance.group(
            com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatVersion.codec()
                    .fieldOf("formatVersion").forGetter(RuntimeManifest::formatVersion),
            NamespacedId.codec().fieldOf("documentId").forGetter(RuntimeManifest::documentId),
            MapKey.codec().fieldOf("binding").forGetter(RuntimeManifest::binding),
            MinimapCodecs.NON_NEGATIVE_LONG.fieldOf("publishRevision").forGetter(RuntimeManifest::publishRevision),
            Sha256.codec().fieldOf("sourceHash").forGetter(RuntimeManifest::sourceHash),
            COMPILER_PROFILE.fieldOf("compilerProfile").forGetter(RuntimeManifest::compilerProfile),
            CANVAS_BOUNDS.fieldOf("canvas").forGetter(RuntimeManifest::canvas),
            DEFAULT_VIEW_MODE.fieldOf("defaultViewMode").forGetter(RuntimeManifest::defaultViewMode),
            Codec.list(RUNTIME_FLOOR).fieldOf("floors").forGetter(RuntimeManifest::floors),
            Codec.INT.fieldOf("tileEdge").forGetter(RuntimeManifest::tileEdge),
            Codec.list(RUNTIME_ENTRY).fieldOf("entries").forGetter(RuntimeManifest::entries)
    ).apply(instance, RuntimeManifest::new));

    private static final Codec<RectangleGeometry> RECTANGLE_GEOMETRY = RecordCodecBuilder.create(instance -> instance.group(
            CANVAS_RECT.fieldOf("bounds").forGetter(RectangleGeometry::bounds)
    ).apply(instance, RectangleGeometry::new));

    private static final Codec<PolygonGeometry> POLYGON_GEOMETRY = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(CANVAS_POINT).fieldOf("vertices").forGetter(PolygonGeometry::vertices)
    ).apply(instance, PolygonGeometry::new));

    public static final Codec<RegionGeometry> REGION_GEOMETRY = GEOMETRY_TYPE.dispatch(
            "type",
            RegionGeometry::type,
            type -> switch (type) {
                case RECTANGLE -> MapCodec.assumeMapUnsafe(RECTANGLE_GEOMETRY);
                case POLYGON -> MapCodec.assumeMapUnsafe(POLYGON_GEOMETRY);
            }
    );

    public static final Codec<FillStyle> FILL_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            RGBA_COLOR.fieldOf("color").forGetter(FillStyle::color),
            Codec.DOUBLE.fieldOf("opacity").forGetter(FillStyle::opacity)
    ).apply(instance, FillStyle::new));

    public static final Codec<StrokeStyle> STROKE_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            RGBA_COLOR.fieldOf("color").forGetter(StrokeStyle::color),
            Codec.DOUBLE.fieldOf("width").forGetter(StrokeStyle::width),
            Codec.DOUBLE.fieldOf("opacity").forGetter(StrokeStyle::opacity)
    ).apply(instance, StrokeStyle::new));

    public static final Codec<TextAppearance> TEXT_APPEARANCE = RecordCodecBuilder.create(instance -> instance.group(
            RGBA_COLOR.fieldOf("color").forGetter(TextAppearance::color),
            Codec.DOUBLE.fieldOf("scale").forGetter(TextAppearance::scale)
    ).apply(instance, TextAppearance::new));

    public static final Codec<IconAppearance> ICON_APPEARANCE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("texture").forGetter(IconAppearance::texture),
            Codec.DOUBLE.fieldOf("scale").forGetter(IconAppearance::scale)
    ).apply(instance, IconAppearance::new));

    public static final Codec<RegionStyleOverride> REGION_STYLE_OVERRIDE = RecordCodecBuilder.create(instance -> instance.group(
            FILL_STYLE.optionalFieldOf("fill").forGetter(RegionStyleOverride::fill),
            STROKE_STYLE.optionalFieldOf("stroke").forGetter(RegionStyleOverride::stroke),
            TEXT_APPEARANCE.optionalFieldOf("label").forGetter(RegionStyleOverride::label)
    ).apply(instance, RegionStyleOverride::new));

    private static final Codec<RegionStyle> REGION_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("id").forGetter(RegionStyle::id),
            FILL_STYLE.fieldOf("fill").forGetter(RegionStyle::fill),
            STROKE_STYLE.fieldOf("stroke").forGetter(RegionStyle::stroke),
            TEXT_APPEARANCE.fieldOf("label").forGetter(RegionStyle::label)
    ).apply(instance, RegionStyle::new));

    private static final Codec<LineStyle> LINE_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("id").forGetter(LineStyle::id),
            STROKE_STYLE.fieldOf("stroke").forGetter(LineStyle::stroke)
    ).apply(instance, LineStyle::new));

    private static final Codec<TextStyle> TEXT_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("id").forGetter(TextStyle::id),
            TEXT_APPEARANCE.fieldOf("text").forGetter(TextStyle::text)
    ).apply(instance, TextStyle::new));

    private static final Codec<IconStyle> ICON_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("id").forGetter(IconStyle::id),
            ICON_APPEARANCE.fieldOf("icon").forGetter(IconStyle::icon)
    ).apply(instance, IconStyle::new));

    public static final Codec<MinimapStyle> STYLE = STYLE_TYPE.dispatch(
            "type",
            MinimapStyle::type,
            type -> switch (type) {
                case REGION -> MapCodec.assumeMapUnsafe(REGION_STYLE);
                case LINE -> MapCodec.assumeMapUnsafe(LINE_STYLE);
                case TEXT -> MapCodec.assumeMapUnsafe(TEXT_STYLE);
                case ICON -> MapCodec.assumeMapUnsafe(ICON_STYLE);
            }
    );

    public static final Codec<MinimapRegion> REGION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MinimapRegion::id),
            Codec.STRING.fieldOf("floorId").forGetter(MinimapRegion::floorId),
            DISPLAY_LABEL.fieldOf("label").forGetter(MinimapRegion::label),
            REGION_GEOMETRY.fieldOf("geometry").forGetter(MinimapRegion::geometry),
            NamespacedId.codec().fieldOf("semanticType").forGetter(MinimapRegion::semanticType),
            Codec.list(NamespacedId.codec()).fieldOf("tags").forGetter(MinimapRegion::tags),
            NamespacedId.codec().optionalFieldOf("gameplayReference").forGetter(MinimapRegion::gameplayReference),
            NamespacedId.codec().fieldOf("styleId").forGetter(MinimapRegion::styleId),
            REGION_STYLE_OVERRIDE.fieldOf("styleOverride").forGetter(MinimapRegion::styleOverride),
            CANVAS_POINT.fieldOf("labelAnchor").forGetter(MinimapRegion::labelAnchor),
            Codec.INT.fieldOf("priority").forGetter(MinimapRegion::priority),
            Codec.DOUBLE.fieldOf("minVisibleScale").forGetter(MinimapRegion::minVisibleScale),
            Codec.DOUBLE.fieldOf("maxVisibleScale").forGetter(MinimapRegion::maxVisibleScale)
    ).apply(instance, MinimapRegion::new));

    public static final Codec<RuntimeRegion> RUNTIME_REGION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(RuntimeRegion::id),
            Codec.STRING.fieldOf("floorId").forGetter(RuntimeRegion::floorId),
            DISPLAY_LABEL.fieldOf("label").forGetter(RuntimeRegion::label),
            REGION_GEOMETRY.fieldOf("geometry").forGetter(RuntimeRegion::geometry),
            NamespacedId.codec().fieldOf("semanticType").forGetter(RuntimeRegion::semanticType),
            Codec.list(NamespacedId.codec()).fieldOf("tags").forGetter(RuntimeRegion::tags),
            NamespacedId.codec().optionalFieldOf("gameplayReference").forGetter(RuntimeRegion::gameplayReference),
            NamespacedId.codec().fieldOf("styleId").forGetter(RuntimeRegion::styleId),
            CANVAS_POINT.fieldOf("labelAnchor").forGetter(RuntimeRegion::labelAnchor),
            Codec.INT.fieldOf("priority").forGetter(RuntimeRegion::priority),
            Codec.DOUBLE.fieldOf("minVisibleScale").forGetter(RuntimeRegion::minVisibleScale),
            Codec.DOUBLE.fieldOf("maxVisibleScale").forGetter(RuntimeRegion::maxVisibleScale)
    ).apply(instance, RuntimeRegion::new));

    public static final Codec<RuntimeStyle> RUNTIME_STYLE = RecordCodecBuilder.create(instance -> instance.group(
            NamespacedId.codec().fieldOf("id").forGetter(RuntimeStyle::id),
            TEXT_APPEARANCE.optionalFieldOf("label").forGetter(RuntimeStyle::label),
            ICON_APPEARANCE.optionalFieldOf("icon").forGetter(RuntimeStyle::icon)
    ).apply(instance, RuntimeStyle::new));

    public static final Codec<ConnectionEndpoint> CONNECTION_ENDPOINT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("floorId").forGetter(ConnectionEndpoint::floorId),
            CANVAS_POINT.fieldOf("point").forGetter(ConnectionEndpoint::point)
    ).apply(instance, ConnectionEndpoint::new));

    public static final Codec<MinimapFloorConnection> CONNECTION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MinimapFloorConnection::id),
            CONNECTION_ENDPOINT.fieldOf("from").forGetter(MinimapFloorConnection::from),
            CONNECTION_ENDPOINT.fieldOf("to").forGetter(MinimapFloorConnection::to),
            CONNECTION_TYPE.fieldOf("type").forGetter(MinimapFloorConnection::type),
            CONNECTION_DISPLAY_DIRECTION.fieldOf("displayDirection")
                    .forGetter(MinimapFloorConnection::displayDirection),
            DISPLAY_LABEL.optionalFieldOf("label").forGetter(MinimapFloorConnection::label)
    ).apply(instance, MinimapFloorConnection::new));

    public static final Codec<RegionsFile> REGIONS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(REGION).fieldOf("regions").forGetter(RegionsFile::regions)
    ).apply(instance, RegionsFile::new));

    public static final Codec<RuntimeRegionsFile> RUNTIME_REGIONS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(RUNTIME_REGION).fieldOf("regions").forGetter(RuntimeRegionsFile::regions)
    ).apply(instance, RuntimeRegionsFile::new));

    public static final Codec<ConnectionsFile> CONNECTIONS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(CONNECTION).fieldOf("connections").forGetter(ConnectionsFile::connections)
    ).apply(instance, ConnectionsFile::new));

    public static final Codec<StylesFile> STYLES = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(STYLE).fieldOf("styles").forGetter(StylesFile::styles)
    ).apply(instance, StylesFile::new));

    public static final Codec<RuntimeStylesFile> RUNTIME_STYLES = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(RUNTIME_STYLE).fieldOf("styles").forGetter(RuntimeStylesFile::styles)
    ).apply(instance, RuntimeStylesFile::new));


    public static final Codec<VectorObjectType> VECTOR_OBJECT_TYPE = enumCodec(VectorObjectType.class);

    private static final Codec<VectorObject> VECTOR_LINE = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(VectorObject::id),
            Codec.STRING.fieldOf("floorId").forGetter(VectorObject::floorId),
            Codec.list(CANVAS_POINT).fieldOf("points").forGetter(VectorObject::linePoints),
            NamespacedId.codec().fieldOf("styleId").forGetter(vector -> vector.styleId().orElseThrow()),
            Codec.DOUBLE.fieldOf("opacity").forGetter(VectorObject::opacity)
    ).apply(instance, VectorObject::line));

    private static final Codec<VectorObject> VECTOR_RECTANGLE = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(VectorObject::id),
            Codec.STRING.fieldOf("floorId").forGetter(VectorObject::floorId),
            RECTANGLE_GEOMETRY.fieldOf("geometry").forGetter(VectorObject::rectangle),
            NamespacedId.codec().fieldOf("styleId").forGetter(vector -> vector.styleId().orElseThrow()),
            FILL_STYLE.optionalFieldOf("fill").forGetter(VectorObject::fill),
            Codec.DOUBLE.fieldOf("opacity").forGetter(VectorObject::opacity)
    ).apply(instance, VectorObject::rectangle));

    private static final Codec<VectorObject> VECTOR_POLYGON = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(VectorObject::id),
            Codec.STRING.fieldOf("floorId").forGetter(VectorObject::floorId),
            POLYGON_GEOMETRY.fieldOf("geometry").forGetter(VectorObject::polygon),
            NamespacedId.codec().fieldOf("styleId").forGetter(vector -> vector.styleId().orElseThrow()),
            FILL_STYLE.optionalFieldOf("fill").forGetter(VectorObject::fill),
            Codec.DOUBLE.fieldOf("opacity").forGetter(VectorObject::opacity)
    ).apply(instance, VectorObject::polygon));

    private static final Codec<VectorObject> VECTOR_TEXT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(VectorObject::id),
            Codec.STRING.fieldOf("floorId").forGetter(VectorObject::floorId),
            CANVAS_POINT.fieldOf("anchor").forGetter(VectorObject::anchor),
            DISPLAY_LABEL.fieldOf("text").forGetter(VectorObject::text),
            NamespacedId.codec().fieldOf("styleId").forGetter(vector -> vector.styleId().orElseThrow()),
            TEXT_APPEARANCE.fieldOf("appearance").forGetter(vector -> vector.textAppearance().orElseThrow()),
            Codec.DOUBLE.fieldOf("opacity").forGetter(VectorObject::opacity)
    ).apply(instance, VectorObject::text));

    private static final Codec<VectorObject> VECTOR_ICON = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(VectorObject::id),
            Codec.STRING.fieldOf("floorId").forGetter(VectorObject::floorId),
            CANVAS_POINT.fieldOf("anchor").forGetter(VectorObject::anchor),
            ICON_APPEARANCE.fieldOf("icon").forGetter(vector -> vector.icon().orElseThrow()),
            Codec.DOUBLE.fieldOf("opacity").forGetter(VectorObject::opacity)
    ).apply(instance, VectorObject::icon));

    public static final Codec<VectorObject> VECTOR_OBJECT = VECTOR_OBJECT_TYPE.dispatch(
            "type",
            VectorObject::type,
            type -> switch (type) {
                case LINE -> MapCodec.assumeMapUnsafe(VECTOR_LINE);
                case RECTANGLE -> MapCodec.assumeMapUnsafe(VECTOR_RECTANGLE);
                case POLYGON -> MapCodec.assumeMapUnsafe(VECTOR_POLYGON);
                case TEXT -> MapCodec.assumeMapUnsafe(VECTOR_TEXT);
                case ICON -> MapCodec.assumeMapUnsafe(VECTOR_ICON);
            }
    );

    public static final Codec<VectorsFile> VECTORS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(VECTOR_OBJECT).fieldOf("vectors").forGetter(VectorsFile::vectors)
    ).apply(instance, VectorsFile::new));

    public static final Codec<VectorsFile> VECTORS_FILE = VECTORS;
    public static final Codec<RegionsFile> REGIONS_FILE = REGIONS;
    public static final Codec<StylesFile> STYLES_FILE = STYLES;
    private MinimapModelCodecs() {
    }

    private static Codec<? extends MinimapLayer> codecForLayerType(LayerType type) {
        return switch (type) {
            case IMPORTED_IMAGE -> IMPORTED_IMAGE_LAYER;
            case WORLD_BAKE -> WORLD_BAKE_LAYER;
            case RASTER_PAINT -> RASTER_PAINT_LAYER;
            case VECTOR -> VECTOR_LAYER;
            case REGION_VISUAL -> REGION_VISUAL_LAYER;
            case CUTOUT -> CUTOUT_LAYER;
        };
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> enumType) {
        return Codec.STRING.flatXmap(
                value -> {
                    try {
                        return DataResult.success(Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException exception) {
                        return DataResult.error(() -> "Unknown " + enumType.getSimpleName() + " value");
                    }
                },
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT))
        );
    }
}
