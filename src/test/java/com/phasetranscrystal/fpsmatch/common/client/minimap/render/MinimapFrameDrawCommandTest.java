package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapFrameDrawCommandTest {
    @Test
    void buildsImmutableFrameWithPlaceholdersIndependentOfGuiGraphics() {
        ViewportCamera camera = ViewportCamera.fixedNorth(0, 0, 1.0, 128, 128);
        MinimapFrame frame = MinimapFrame.builder()
                .camera(camera)
                .shape(ShapeMode.CIRCLE)
                .floor(FloorViewState.automatic("ground"))
                .addCommand(new MapDrawCommand.Tile("tiles/ground_0_0.png", 0, 0, 64, 64, 1f))
                .addCommand(new MapDrawCommand.MarkerIcon("fpsmatch:m1", 10, 12, 0f, 1f, false))
                .addCommand(new MapDrawCommand.Label("A Site", 8, 4, 1f))
                .placeholder(PlaceholderKind.LOADING)
                .build();
        assertEquals(ShapeMode.CIRCLE, frame.shape());
        assertEquals(3, frame.commands().size());
        assertEquals(PlaceholderKind.LOADING, frame.placeholder().orElseThrow());
        // immutability
        List<MapDrawCommand> commands = frame.commands();
        assertTrue(commands.get(0) instanceof MapDrawCommand.Tile);
    }
}