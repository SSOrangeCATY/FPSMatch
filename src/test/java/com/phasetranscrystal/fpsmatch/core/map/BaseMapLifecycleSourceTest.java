package com.phasetranscrystal.fpsmatch.core.map;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseMapLifecycleSourceTest {
    @Test
    void resetClearsLegacyAndLifecycleStartedState() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/map/BaseMap.java"
        ));

        assertTrue(source.contains("this.lifecycleState.reset();"));
        assertTrue(source.contains("this.isStart = false;"));
    }
}
