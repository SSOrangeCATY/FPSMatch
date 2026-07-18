package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ldlib2MinimapHudAdapterTest {
    private static final String ADAPTER =
            "com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2"
                    + ".Ldlib2MinimapHudAdapter";
    private static final String CANVAS =
            "com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2"
                    + ".Ldlib2MinimapCanvasElement";
    private static final String PRESENTATION =
            "com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2"
                    + ".Ldlib2MinimapHudPresentation";

    @Test
    void exposesStableWidgetIdsWithoutLoadingLdlib2() {
        assertEquals(
                List.of(
                        "minimap.hud.root",
                        "minimap.hud.canvas",
                        "minimap.hud.config_preview",
                        "minimap.hud.placeholder",
                        "minimap.hud.compass"
                ),
                MinimapHudWidgetCatalog.defaultCatalog().ids()
        );
    }

    @Test
    void layoutModelRejectsNonPositiveDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MinimapHudLayoutModel(0, 0, 0, 128)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MinimapHudLayoutModel(0, 0, 128, 0)
        );
    }

    @Test
    void compiledAdapterUsesRealLdlib2TypesWithoutInitializingThem()
            throws IOException {
        ClassNode adapter = classNode(ADAPTER);
        ClassNode canvas = classNode(CANVAS);

        assertTrue(adapter.interfaces.contains(
                "com/lowdragmc/lowdraglib2/gui/hud/ModularHudLayer"
        ));
        assertEquals(
                "com/lowdragmc/lowdraglib2/gui/ui/UIElement",
                canvas.superName
        );
        assertField(
                adapter,
                "canvas",
                "Lcom/phasetranscrystal/fpsmatch/common/client/minimap/ui/ldlib2/"
                        + "Ldlib2MinimapCanvasElement;"
        );
        assertMethod(
                adapter,
                "getModularUI",
                "()Lcom/lowdragmc/lowdraglib2/gui/ui/ModularUI;"
        );
        assertMethod(
                canvas,
                "drawBackgroundAdditional",
                "(Lcom/lowdragmc/lowdraglib2/gui/ui/rendering/GUIContext;)V"
        );
        assertField(
                canvas,
                "markerPresentations",
                "Lcom/phasetranscrystal/fpsmatch/common/client/minimap/render/"
                        + "MarkerPresentationResolver;"
        );
    }

    @Test
    void compiledPresentationOwnsOneAdapterAndOneFrameService()
            throws IOException {
        ClassNode presentation = classNode(PRESENTATION);

        assertField(
                presentation,
                "frames",
                "Lcom/phasetranscrystal/fpsmatch/common/client/minimap/render/"
                        + "ClientMinimapHudPresentationService;"
        );
        assertField(
                presentation,
                "adapter",
                "Lcom/phasetranscrystal/fpsmatch/common/client/minimap/ui/ldlib2/"
                        + "Ldlib2MinimapHudAdapter;"
        );
        assertTrue(presentation.methods.stream()
                .map(MethodNode.class::cast)
                .anyMatch(method -> method.name.equals("render")
                        && method.desc.endsWith(")Z")));
        assertMethod(presentation, "reset", "()V");
    }

    private static ClassNode classNode(String binaryName) throws IOException {
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream input = Ldlib2MinimapHudAdapterTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(
                    node,
                    ClassReader.SKIP_CODE
                            | ClassReader.SKIP_DEBUG
                            | ClassReader.SKIP_FRAMES
            );
            return node;
        }
    }

    private static void assertField(
            ClassNode owner,
            String name,
            String descriptor
    ) {
        assertTrue(owner.fields.stream()
                .map(FieldNode.class::cast)
                .anyMatch(field -> field.name.equals(name)
                        && field.desc.equals(descriptor)));
    }

    private static void assertMethod(
            ClassNode owner,
            String name,
            String descriptor
    ) {
        assertTrue(owner.methods.stream()
                .map(MethodNode.class::cast)
                .anyMatch(method -> method.name.equals(name)
                        && method.desc.equals(descriptor)));
    }
}
