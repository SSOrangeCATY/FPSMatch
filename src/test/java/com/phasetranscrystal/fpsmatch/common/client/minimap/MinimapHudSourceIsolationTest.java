package com.phasetranscrystal.fpsmatch.common.client.minimap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapHudSourceIsolationTest {
    @Test
    void coreAndServerPackagesNeverImportUiLibraries() throws IOException {
        for (Path root : List.of(
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/minimap"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/minimap/server")
        )) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("com.lowdragmc.ldlib2"), file.toString());
                    assertFalse(source.contains("com.mojang.blaze3d"), file.toString());
                    assertFalse(source.contains("icyllis.modernui"), file.toString());
                    assertFalse(source.contains("me.shedaniel.cloth"), file.toString());
                }
            }
        }
    }

    @Test
    void hudRendererAndFramePackagesDoNotImportLdlibModernUiOrCloth() throws IOException {
        Path root = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/minimap/render");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("com.lowdragmc.ldlib2"), file.toString());
                assertFalse(source.contains("icyllis.modernui"), file.toString());
                assertFalse(source.contains("me.shedaniel.cloth"), file.toString());
            }
        }
    }

    @Test
    void ldlib2HudAdapterIsOnlyPresentationBoundary() throws IOException {
        Path adapter = Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/minimap/ui/ldlib2/Ldlib2MinimapHudAdapter.java"
        );
        assertTrue(Files.exists(adapter));
        String source = Files.readString(adapter);
        assertTrue(source.contains(
                "import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;"
        ));
        assertTrue(source.contains("implements ModularHudLayer"));
        assertFalse(source.contains("import icyllis.modernui"));
        assertFalse(source.contains("import me.shedaniel.cloth"));
        assertFalse(source.contains("import net.minecraft.client.gui.GuiGraphics"));
    }

    @Test
    void gameHudManagerUsesResolvedPlacementAndClientBootstrapInstallsOnce()
            throws IOException {
        String manager = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/"
                        + "FPSMGameHudManager.java"
        ));
        String registrar = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/net/"
                        + "FPSMClientPacketRegistrar.java"
        ));

        assertTrue(manager.contains("registerResolvedGlobalHud("));
        assertTrue(manager.contains("globalHudCatalog.renderRegistered("));
        assertTrue(manager.contains("minimapHud.render("));
        assertTrue(registrar.contains(
                "FPSMGameHudManager.INSTANCE.installMinimap(minimapServices);"
        ));

        String client = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/"
                        + "FPSMClient.java"
        ));
        assertTrue(client.contains("RegisterClientReloadListenersEvent"));
        assertTrue(client.contains("registerReloadListener"));
        assertTrue(manager.contains("onMinimapResourceReload"));
        assertTrue(manager.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(manager.contains("FPSMClientResetEvent"));
    }
}
