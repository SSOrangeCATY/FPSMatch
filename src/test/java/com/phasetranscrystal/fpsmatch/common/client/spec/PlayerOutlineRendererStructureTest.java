package com.phasetranscrystal.fpsmatch.common.client.spec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOutlineRendererStructureTest {

    @Test
    void playerOutlineDoesNotDependOnVanillaGlowingMixin() throws IOException {
        String mixins = Files.readString(Path.of("src/main/resources/fpsmatch.mixins.json"));

        assertFalse(mixins.contains("spec.glow.MixinEntityUnified"));
    }

    @Test
    void customPlayerOutlineRendererOwnsTeamAndEnemyGlowDecision() throws IOException {
        Path rendererPath = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/PlayerOutlineRenderer.java");
        String renderer = Files.readString(rendererPath);

        assertTrue(renderer.contains("RenderPlayerEvent.Post"));
        assertTrue(renderer.contains("data.isEnemyGlow()"));
        assertTrue(renderer.contains("data.isTeamGlow()"));
        assertTrue(renderer.contains("event.getRenderer().render"));
        assertTrue(renderer.contains("outlineBufferSource"));
    }

    @Test
    void spectatorGlowManagerDoesNotWriteVanillaGlowingTag() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/SpectatorGlowManager.java"));

        assertFalse(manager.contains("setGlowingTag"));
    }
}
