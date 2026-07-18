package com.phasetranscrystal.fpsmatch.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinRunMappingContractTest {

    @Test
    void clientAndServerRunsUseTheGeneratedSrgToNamedMapping() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        String expected =
                "${projectDir}/build/moddev/artifacts/intermediateToNamed.srg";

        assertEquals(2, occurrences(build,
                "systemProperty 'mixin.env.remapRefMap', 'true'"));
        assertEquals(2, occurrences(build,
                "systemProperty 'mixin.env.refMapRemappingFile', \""
                        + expected + "\""));
        assertFalse(build.contains("build/createSrgToMcp/output.srg"));
        assertTrue(build.contains("tasks.named('createMinecraftArtifacts')"));
    }

    @Test
    void generatedMappingExistsAndMapsLivingEntityTick() throws IOException {
        Path mapping = Path.of(
                "build/moddev/artifacts/intermediateToNamed.srg"
        );
        assertTrue(Files.isRegularFile(mapping));
        String contents = Files.readString(mapping);
        assertTrue(contents.contains(
                "MD: net/minecraft/world/entity/Entity/m_8119_ ()V "
                        + "net/minecraft/world/entity/Entity/tick ()V"
        ));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = text.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }
}
