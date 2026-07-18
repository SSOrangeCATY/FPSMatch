package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FPSMatchMinimapGameTestResourceTest {
    @Test
    void dedicatedMinimapGameTestsAndEmptyTemplateArePackaged()
            throws Exception {
        Path gameTests = Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/minimap/server/sync/"
                        + "FPSMatchMinimapGameTests.java"
        );
        Path structure = Path.of(
                "src/main/resources/data/fpsmatch/gameteststructures/empty.snbt"
        );

        assertTrue(Files.isRegularFile(gameTests));
        String source = Files.readString(gameTests, StandardCharsets.UTF_8);
        assertTrue(source.contains("@GameTestHolder(\"fpsmatch\")"));
        assertTrue(source.contains("@GameTest(template = \"empty\")"));
        assertTrue(source.contains("serverRuntimeLifecycle"));
        assertTrue(source.contains("subscriptionCleanup"));
        assertTrue(source.contains("mapDimensionSwitchAndReconnectCleanup"));

        assertTrue(Files.isRegularFile(structure));
        String snbt = Files.readString(structure, StandardCharsets.UTF_8);
        assertTrue(snbt.contains("size: [1, 1, 1]"));
        assertTrue(snbt.contains("palette"));
        assertTrue(snbt.contains("blocks"));
        assertTrue(snbt.contains("entities"));
    }

    @Test
    void gameTestPreparationCopiesTemplateIntoIsolatedRunDirectory()
            throws Exception {
        String build = Files.readString(
                Path.of("build.gradle"), StandardCharsets.UTF_8
        );

        assertTrue(build.contains("prepareFPSMatchGameTestStructures"));
        assertTrue(build.contains(
                "src/main/resources/data/fpsmatch/gameteststructures"
        ));
        assertTrue(build.contains("run-gametest/gameteststructures"));
        assertTrue(build.contains("tasks.named('prepareGameTestServerRun')"));
    }
}
