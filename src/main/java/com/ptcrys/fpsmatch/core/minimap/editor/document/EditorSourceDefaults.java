package com.ptcrys.fpsmatch.core.minimap.editor.document;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.ConnectionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.ControlPoint;
import com.ptcrys.fpsmatch.core.minimap.model.DefaultViewMode;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.FloorBackground;
import com.ptcrys.fpsmatch.core.minimap.model.FloorCalibration;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapFloor;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapLayer;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.RegionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.RgbaColor;
import com.ptcrys.fpsmatch.core.minimap.model.SourceDocument;
import com.ptcrys.fpsmatch.core.minimap.model.SourceFloor;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;
import com.ptcrys.fpsmatch.core.minimap.model.StylesFile;
import com.ptcrys.fpsmatch.core.minimap.model.WorldBounds;
import com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns the shared world, floor, and calibration policy for new editor sources. */
public final class EditorSourceDefaults {
    private EditorSourceDefaults() {
    }

    public static MinimapDefinition createDefinition(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            CanvasBounds canvas,
            int tileEdge,
            String floorId,
            List<? extends MinimapLayer> layers
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(floorId, "floorId");
        List<MinimapLayer> copiedLayers = List.copyOf(layers);
        double worldMaxX = canvas.width();
        double worldMaxZ = canvas.height();
        SourceFloor floor = new SourceFloor(
                new MinimapFloor(floorId, -64, 320, 0, 0.5, 1),
                DisplayLabel.literal(floorId),
                Optional.empty(),
                new FloorBackground(new RgbaColor(0, 0, 0, 0)),
                new FloorCalibration(List.of(
                        new ControlPoint(new WorldPoint2D(0, 0), new CanvasPoint(0, 0)),
                        new ControlPoint(new WorldPoint2D(worldMaxX, 0),
                                new CanvasPoint(canvas.width(), 0)),
                        new ControlPoint(new WorldPoint2D(0, worldMaxZ),
                                new CanvasPoint(0, canvas.height()))
                ), false, 2),
                copiedLayers
        );
        SourceDocument document = new SourceDocument(
                new WorldBounds(0, 0, worldMaxX, worldMaxZ),
                canvas,
                DefaultViewMode.FULL_MAP,
                List.of(floor),
                Map.of(floorId, copiedLayers.stream().map(layer -> layer.common().id()).toList())
        );
        SourceManifest manifest = new SourceManifest(
                MinimapFormatContract.CURRENT,
                documentId,
                mapKey,
                revision,
                dimension,
                Optional.empty(),
                tileEdge,
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
}
