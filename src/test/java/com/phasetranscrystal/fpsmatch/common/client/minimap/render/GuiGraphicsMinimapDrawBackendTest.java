package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ViewportCamera;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapGameplayExtension;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiGraphicsMinimapDrawBackendTest {
    @AfterEach
    void clearExtensions() {
        MinimapExtensionRegistry.clearForTests();
    }

    @Test
    void minecraftTargetIsAConcreteGuiGraphicsAdapter() throws Exception {
        assertTrue(GuiGraphicsMinimapDrawBackend.DrawTarget.class.isAssignableFrom(
                MinecraftGuiGraphicsMinimapDrawTarget.class
        ));
        MinecraftGuiGraphicsMinimapDrawTarget.class.getConstructor(
                GuiGraphics.class,
                MinimapTextureResolver.class
        );
    }

    @Test
    void minecraftTargetExecutesResolvedMarkerTexturePlans() throws Exception {
        ClassNode target = classNode(MinecraftGuiGraphicsMinimapDrawTarget.class);
        boolean createsPlan = target.methods.stream()
                .filter(method -> method.name.equals("marker"))
                .flatMap(method -> java.util.stream.StreamSupport.stream(
                        method.instructions.spliterator(), false
                ))
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals(
                        "com/phasetranscrystal/fpsmatch/common/client/minimap/render/MarkerTextureDrawPlan"
                ) && call.name.equals("create"));
        boolean blitsTexture = target.methods.stream()
                .flatMap(method -> java.util.stream.StreamSupport.stream(
                        method.instructions.spliterator(), false
                ))
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/minecraft/client/gui/GuiGraphics")
                        && call.name.equals("blit"));

        assertTrue(createsPlan);
        assertTrue(blitsTexture);
    }

    @Test
    void mapsCanvasCommandsIntoThePlacedViewport() {
        RecordingTarget target = new RecordingTarget();
        GuiGraphicsMinimapDrawBackend backend = new GuiGraphicsMinimapDrawBackend(
                target, 10, 20, 128, 128
        );
        MinimapFrame frame = MinimapFrame.builder()
                .camera(ViewportCamera.fixedNorth(32, 32, 2, 128, 128))
                .shape(ShapeMode.CIRCLE)
                .backgroundOpacity(0.25f)
                .floor(FloorViewState.automatic("ground"))
                .addCommand(new MapDrawCommand.Tile(
                        "floors/ground/tiles/0/0_0.png", 0, 0, 64, 64, 0.75f
                ))
                .addCommand(new MapDrawCommand.MarkerIcon(
                        "fpsmatch:self",
                        NamespacedId.parse("fpsmatch:type/player"),
                        NamespacedId.parse("blockoffensive:style/self"),
                        32, 32, 15f, 1f, false
                ))
                .placeholder(PlaceholderKind.LOADING)
                .build();

        backend.submit(frame);

        assertEquals(ShapeMode.CIRCLE, target.shape);
        assertEquals(0.25f, target.backgroundOpacity, 1e-6);
        assertEquals(new Rect(10, 20, 128, 128), target.clip);
        assertEquals(
                new Texture("floors/ground/tiles/0/0_0.png", 10, 20, 128, 128, 0.75f, 0f, 74, 84),
                target.textures.get(0)
        );
        assertEquals(
                new Marker(
                        "fpsmatch:self",
                        NamespacedId.parse("fpsmatch:type/player"),
                        NamespacedId.parse("blockoffensive:style/self"),
                        74, 84, 15f, 1f, false, Optional.empty()
                ),
                target.markers.get(0)
        );
        assertEquals(PlaceholderKind.LOADING, target.placeholder);
        assertEquals(1, target.endCount);
    }

    @Test
    void playerUpCameraRotatesTilesAndProjectsMarkersAroundViewportCenter() {
        RecordingTarget target = new RecordingTarget();
        GuiGraphicsMinimapDrawBackend backend = new GuiGraphicsMinimapDrawBackend(
                target, 0, 0, 128, 128
        );
        MinimapFrame frame = MinimapFrame.builder()
                .camera(ViewportCamera.playerUp(32, 32, 2, 128, 128, 90f))
                .shape(ShapeMode.SQUARE)
                .floor(FloorViewState.automatic("ground"))
                .addCommand(new MapDrawCommand.Tile("tile", 0, 0, 64, 64, 1f))
                .addCommand(new MapDrawCommand.MarkerIcon(
                        "fpsmatch:self", 40, 32, 90f, 1f, false
                ))
                .build();

        backend.submit(frame);

        assertEquals(90f, target.textures.get(0).rotationDegrees(), 1e-6);
        assertEquals(64.0, target.markers.get(0).x(), 1e-6);
        assertEquals(80.0, target.markers.get(0).y(), 1e-6);
        assertEquals(0f, target.markers.get(0).yawDegrees(), 1e-6);
    }

    @Test
    void passesResolvedMarkerPresentationToTheDrawTarget() {
        MapKey mapKey = new MapKey("cs", "dust2");
        NamespacedId typeId = NamespacedId.parse("fpsmatch:type/player");
        NamespacedId styleId = NamespacedId.parse("blockoffensive:style/ally");
        NamespacedId textureId = NamespacedId.parse(
                "blockoffensive:textures/minimap/markers/ally.png"
        );
        MinimapExtensionRegistry.register(new MinimapGameplayExtension() {
            @Override public String id() { return "test:draw-marker"; }
            @Override public boolean supports(MapKey requested) { return mapKey.equals(requested); }
            @Override public List<MarkerPresentation> markerPresentations(MapKey requested) {
                return List.of(new MarkerPresentation(
                        typeId, styleId, textureId,
                        DisplayLabel.translation("blockoffensive.minimap.marker.ally"),
                        1.0
                ));
            }
        });
        RuntimeGeneration generation = new RuntimeGeneration(
                1L, "server-a", mapKey,
                NamespacedId.parse("fpsmatch:document/test"), 1L,
                Sha256Digest.of("runtime".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                NamespacedId.parse("minecraft:overworld"), 1L
        );
        MarkerPresentationResolver presentations = new MarkerPresentationResolver(
                () -> Optional.of(generation), ignored -> true
        );
        RecordingTarget target = new RecordingTarget();
        GuiGraphicsMinimapDrawBackend backend = new GuiGraphicsMinimapDrawBackend(
                target, 0, 0, 128, 128, presentations
        );
        MinimapFrame frame = MinimapFrame.builder()
                .camera(ViewportCamera.fixedNorth(32, 32, 2, 128, 128))
                .floor(FloorViewState.automatic("ground"))
                .addCommand(new MapDrawCommand.MarkerIcon(
                        "fpsmatch:ally", typeId, styleId,
                        32, 32, 0f, 1f, false
                ))
                .addCommand(new MapDrawCommand.MarkerIcon(
                        "fpsmatch:unknown", typeId,
                        NamespacedId.parse("blockoffensive:style/unknown"),
                        40, 32, 0f, 1f, false
                ))
                .build();

        backend.submit(frame);

        assertEquals(textureId, target.markers.get(0).presentation()
                .orElseThrow().textureId());
        assertTrue(target.markers.get(1).presentation().isEmpty());
    }

    private static final class RecordingTarget
            implements GuiGraphicsMinimapDrawBackend.DrawTarget {
        private ShapeMode shape;
        private float backgroundOpacity;
        private Rect clip;
        private final List<Texture> textures = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();
        private PlaceholderKind placeholder;
        private int endCount;

        @Override
        public void begin(
                ShapeMode shape,
                double x,
                double y,
                double width,
                double height,
                float backgroundOpacity
        ) {
            this.shape = shape;
            this.clip = new Rect(x, y, width, height);
            this.backgroundOpacity = backgroundOpacity;
        }

        @Override
        public void texture(
                String textureKey,
                double x,
                double y,
                double width,
                double height,
                float opacity,
                float rotationDegrees,
                double rotationCenterX,
                double rotationCenterY
        ) {
            textures.add(new Texture(
                    textureKey, x, y, width, height, opacity, rotationDegrees,
                    rotationCenterX, rotationCenterY
            ));
        }

        @Override
        public void marker(
                String markerId,
                NamespacedId typeId,
                NamespacedId styleId,
                double x,
                double y,
                float yawDegrees,
                float opacity,
                boolean adjacent,
                Optional<MarkerPresentationResolver.Resolved> presentation
        ) {
            markers.add(new Marker(
                    markerId, typeId, styleId,
                    x, y, yawDegrees, opacity, adjacent, presentation
            ));
        }

        @Override
        public void label(String text, double x, double y, float opacity) {
        }

        @Override
        public void region(String regionId, double[] pointsXY, float opacity) {
        }

        @Override
        public void placeholder(PlaceholderKind placeholder, double centerX, double centerY) {
            this.placeholder = placeholder;
        }

        @Override
        public void end() {
            endCount++;
        }
    }

    private record Rect(double x, double y, double width, double height) {
    }

    private record Texture(
            String key,
            double x,
            double y,
            double width,
            double height,
            float opacity,
            float rotationDegrees,
            double rotationCenterX,
            double rotationCenterY
    ) {
    }

    private record Marker(
            String id,
            NamespacedId typeId,
            NamespacedId styleId,
            double x,
            double y,
            float yawDegrees,
            float opacity,
            boolean adjacent,
            Optional<MarkerPresentationResolver.Resolved> presentation
    ) {
    }

    private static ClassNode classNode(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (var input = type.getResourceAsStream(resource)) {
            ClassNode node = new ClassNode();
            new ClassReader(java.util.Objects.requireNonNull(input)).accept(node, 0);
            return node;
        }
    }
}
