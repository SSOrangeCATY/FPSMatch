package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ControlPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DefaultViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FloorBackground;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FloorCalibration;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ImportedImageLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerCommon;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BlendMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.WorldPoint2D;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MinimapContainerFixtures {
    private MinimapContainerFixtures() {
    }

    public static MinimapDefinition sourceDefinition() {
        SourceFloor floor = new SourceFloor(
                new MinimapFloor("ground", -16, 32, 0, 0.5, 1),
                DisplayLabel.literal("Ground"),
                Optional.empty(),
                new FloorBackground(new RgbaColor(0, 0, 0, 0)),
                new FloorCalibration(List.of(
                        point(0, 0, 0, 0),
                        point(100, 0, 64, 0),
                        point(0, 100, 0, 64)
                ), false, 2),
                List.of()
        );
        SourceDocument document = new SourceDocument(
                new WorldBounds(0, 0, 100, 100),
                new CanvasBounds(64, 64),
                DefaultViewMode.FULL_MAP,
                List.of(floor),
                Map.of("ground", List.of())
        );
        SourceManifest manifest = new SourceManifest(
                MinimapFormatContract.CURRENT,
                NamespacedId.parse("fpsmatch:test-map"),
                new MapKey("fpsmatch:test", "Test Map"),
                7,
                NamespacedId.parse("minecraft:overworld"),
                Optional.empty(),
                64,
                List.of()
        );
        return new MinimapDefinition(
                manifest,
                document,
                new RegionsFile(List.of()),
                new ConnectionsFile(List.of()),
                new StylesFile(List.of())
        );
    }

    public static byte[] fullRuntimeTile() {
        return CanonicalPngCodecV1.encode(64, 64, new byte[64 * 64 * 4]);
    }

    public static MinimapDefinition sourceDefinitionWithImportedAssets() {
        MinimapDefinition base = sourceDefinition();
        SourceFloor baseFloor = base.document().floors().get(0);
        ImportedImageLayer first = new ImportedImageLayer(
                new LayerCommon("image_a", DisplayLabel.literal("A"), true, false, 1,
                        BlendMode.NORMAL, Optional.empty(), false), "a"
        );
        ImportedImageLayer second = new ImportedImageLayer(
                new LayerCommon("image_b", DisplayLabel.literal("B"), true, false, 1,
                        BlendMode.NORMAL, Optional.empty(), false), "b"
        );
        SourceFloor floor = new SourceFloor(
                baseFloor.selection(), baseFloor.label(), baseFloor.contentBounds(),
                baseFloor.background(), baseFloor.calibration(), List.of(first, second)
        );
        SourceDocument document = new SourceDocument(
                base.document().worldBounds(), base.document().canvas(),
                base.document().defaultViewMode(), List.of(floor),
                Map.of("ground", List.of("image_a", "image_b"))
        );
        return new MinimapDefinition(
                base.manifest(), document, base.regions(), base.connections(), base.styles()
        );
    }

    public static List<CanonicalZipWriter.EntrySource> fullRuntimeTiles() {
        return List.of(new CanonicalZipWriter.Entry(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse(
                        "floors/ground/tiles/0/0_0.png"
                ),
                fullRuntimeTile()
        ));
    }

    private static ControlPoint point(double x, double z, double u, double v) {
        return new ControlPoint(new WorldPoint2D(x, z), new CanvasPoint(u, v));
    }
}
