package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Ldlib2TacticalMapScreenContractTest {
    @Test
    void tacticalScreenIsARealLdlib2ScreenWithStableControlsAndInput()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/"
                        + "minimap/ui/ldlib2/Ldlib2TacticalMapScreen.java"
        ));

        assertTrue(source.contains("extends ModularUIScreen"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.CANVAS"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.FLOOR_SELECTOR"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.AUTO_MANUAL"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.FILTER_LIST"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.LEGEND"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.REGION_DETAIL"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.FIT"));
        assertTrue(source.contains("TacticalMapWidgetCatalog.CLOSE"));
        assertTrue(source.contains("UIEvents.MOUSE_WHEEL"));
        assertTrue(source.contains("UIEvents.MOUSE_DOWN"));
        assertTrue(source.contains("UIEvents.DRAG_UPDATE"));
        assertTrue(source.contains("UIEvents.CLICK"));
        assertTrue(source.contains("startDrag(null, null)"));
        assertTrue(source.contains("canvas.getPositionX()"));
        assertTrue(source.contains("canvas.getSizeWidth()"));
        assertTrue(source.contains("prepareTactical"));
        assertTrue(source.contains("current.regionAt"));
        assertTrue(source.contains("setCandidates(floorCandidates"));
        assertTrue(source.contains("filterList.clearAllChildren()"));
        assertTrue(source.contains("legend.clearAllChildren()"));
        assertTrue(source.contains("entry.label()"));
        assertTrue(source.contains("new Toggle()"));
        assertTrue(source.contains("controller.fitFloor()"));
        assertTrue(source.contains("controller.fitAll()"));
        assertTrue(source.contains("Minecraft.getInstance().setScreen(null)"));
        assertTrue(source.contains("setOnClick"));
        assertTrue(source.contains("onClose"));

        String canvas = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/"
                        + "minimap/ui/ldlib2/Ldlib2MinimapCanvasElement.java"
        ));
        assertTrue(canvas.contains("setAllowHitTest(interactive)"));
    }

    @Test
    void ackBridgeOpensScreenOnlyThroughAnInjectedFactory()
            throws IOException {
        String bridge = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/"
                        + "minimap/tactical/MinimapClientScreens.java"
        ));
        String registrar = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/net/"
                        + "FPSMClientPacketRegistrar.java"
        ));
        String opener = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/"
                        + "minimap/ui/ldlib2/Ldlib2TacticalScreenOpener.java"
        ));

        assertTrue(bridge.contains("ScreenOpener"));
        assertTrue(bridge.contains("screenOpener.open"));
        assertTrue(bridge.contains("controller.openAcknowledged"));
        assertTrue(registrar.contains("Ldlib2TacticalScreenOpener"));
        assertTrue(opener.contains("implements MinimapClientScreens.ScreenOpener"));
        assertTrue(opener.contains("new Ldlib2TacticalMapScreen"));
        assertTrue(opener.contains("void close()"));
        assertTrue(opener.contains("Minecraft.getInstance().setScreen(null)"));
    }
}
