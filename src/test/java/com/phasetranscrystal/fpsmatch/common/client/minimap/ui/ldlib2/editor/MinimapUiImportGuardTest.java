package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MinimapUiImportGuardTest {
    @Test
    void minimapUiPackagesDoNotImportModernUiOrCloth() throws IOException {
        Path root = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/minimap");
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("icyllis.modernui"), file.toString());
                assertFalse(source.contains("me.shedaniel.cloth"), file.toString());
                assertFalse(source.contains("me.shedaniel.autoconfig"), file.toString());
            }
        }
    }

    @Test
    void controllerPackageNeverReferencesLdlibWidgets() throws IOException {
        Path root = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/minimap/editor");
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("com.lowdragmc.ldlib2"), file.toString());
                assertFalse(source.contains("net.minecraft.client"), file.toString());
            }
        }
    }
}