package com.phasetranscrystal.fpsmatch.common.client.spec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOutlineRendererStructureTest {

    @Test
    void playerOutlineUsesVanillaGlowingMixin() throws IOException {
        String mixins = Files.readString(Path.of("src/main/resources/fpsmatch.mixins.json"));

        assertTrue(mixins.contains("spec.glow.MixinEntityClientOutline"));
    }

    @Test
    void playerOutlineRendererOnlyOwnsTeamAndEnemyGlowDecision() throws IOException {
        Path rendererPath = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/PlayerOutlineRenderer.java");
        String renderer = Files.readString(rendererPath);

        assertTrue(renderer.contains("data.isEnemyGlow()"));
        assertTrue(renderer.contains("data.isTeamGlow()"));
        assertTrue(renderer.contains("SpectatorGlowManager.shouldGlow"));
        assertTrue(renderer.contains("getOutlineColor"));
        assertFalse(renderer.contains("RenderPlayerEvent.Post"));
        assertFalse(renderer.contains("event.getRenderer().render"));
        assertFalse(renderer.contains("OutlineBufferSource"));
        assertFalse(renderer.contains("outlineBufferSource"));
    }

    @Test
    void clientOutlineMixinHooksVanillaGlowingAndTeamColor() throws IOException {
        String mixin = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/spec/glow/MixinEntityClientOutline.java"));

        assertTrue(mixin.contains("isCurrentlyGlowing"));
        assertTrue(mixin.contains("getTeamColor"));
        assertTrue(mixin.contains("PlayerOutlineRenderer.shouldOutline"));
        assertTrue(mixin.contains("PlayerOutlineRenderer.getOutlineColor"));
    }

    @Test
    void spectatorGlowManagerDoesNotWriteVanillaGlowingTag() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/SpectatorGlowManager.java"));

        assertFalse(manager.contains("setGlowingTag"));
    }
}
