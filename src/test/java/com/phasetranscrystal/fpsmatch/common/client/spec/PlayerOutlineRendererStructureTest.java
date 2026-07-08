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
        assertTrue(renderer.contains("controlsOutline"));
        assertTrue(renderer.contains("isCurrentMatchScoreboardPlayer"));
        assertTrue(renderer.contains("isSameCurrentMatchClientTeam"));
        assertTrue(renderer.contains("isSameCurrentMatchScoreboardTeam"));
        assertTrue(renderer.contains("data.getCurrentGameType() + \"_\" + data.getCurrentMap() + \"_\""));
        assertTrue(renderer.contains("isNormalMatchPlayer"));
        assertTrue(renderer.contains("isCurrentMatchNormalTeam"));
        assertTrue(renderer.contains("isLocalCurrentMatchNormalTeam"));
        assertTrue(renderer.contains("getCurrentMatchNormalTeamName(data)"));
        assertTrue(renderer.contains("data.getCurrentTeam()"));
        assertTrue(renderer.contains("FPSMClientGlobalData.NONE_VALUE.equals(teamName)"));
        assertTrue(renderer.contains("isSpectatorTeamName(team.name)"));
        assertTrue(renderer.contains("FPSMClientGlobalData.SPECTATOR_TEAM.equals(teamName)"));
        assertFalse(renderer.contains("orElseGet(() -> data.isSameTeam(localPlayer, player))"));
        assertFalse(renderer.contains("RenderPlayerEvent.Post"));
        assertFalse(renderer.contains("event.getRenderer().render"));
        assertFalse(renderer.contains("OutlineBufferSource"));
        assertFalse(renderer.contains("outlineBufferSource"));
    }

    @Test
    void playerOutlinePrefersCurrentMatchClientTeamBeforeScoreboardFallback() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/PlayerOutlineRenderer.java"));
        String sameVisibleTeam = renderer.substring(
                renderer.indexOf("private static Optional<Boolean> isSameVisibleTeam"),
                renderer.indexOf("static Optional<Boolean> isSameCurrentMatchClientTeam")
        );

        assertTrue(sameVisibleTeam.contains("isSameCurrentMatchClientTeam(data, localPlayer, player)"));
        assertTrue(sameVisibleTeam.contains(".or(() -> isSameCurrentMatchScoreboardTeam(data, localPlayer, player))"));
        assertFalse(sameVisibleTeam.contains("data.isSameTeam"));
    }

    @Test
    void localTeamNameFallbackSuppressesEnemyGlowDuringPacketOrderingGap() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/PlayerOutlineRenderer.java"));
        String localNormalTeam = renderer.substring(
                renderer.indexOf("static boolean isLocalCurrentMatchNormalTeam"),
                renderer.indexOf("private static boolean isLocalCurrentMatchSpectatorTeam")
        );

        assertTrue(localNormalTeam.contains("getCurrentMatchNormalTeamName(data).isPresent()"));
        assertTrue(renderer.contains(".or(() -> Optional.of(data.getCurrentTeam())"));
        assertTrue(renderer.contains("isCurrentMatchNormalTeamName(data, teamName)"));
        assertTrue(renderer.contains("!FPSMClientGlobalData.NONE_VALUE.equals(teamName)"));
        assertTrue(renderer.contains("!isSpectatorTeamName(teamName)"));
    }

    @Test
    void spectatorGlowUsesCurrentMatchTeamIdentity() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/SpectatorGlowManager.java"));

        assertTrue(manager.contains("PlayerOutlineRenderer.getCurrentMatchClientTeam(data)"));
        assertTrue(manager.contains("PlayerOutlineRenderer.isCurrentMatchSpectatorTeam(data, team)"));
        assertTrue(manager.contains("PlayerOutlineRenderer.isSameCurrentMatchClientTeam(data, mc.player, target)"));
        assertFalse(manager.contains("data.isSameTeam(mc.player, target)"));
    }

    @Test
    void clientOutlineMixinHooksVanillaGlowingAndTeamColor() throws IOException {
        String mixin = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/spec/glow/MixinEntityClientOutline.java"));

        assertTrue(mixin.contains("isCurrentlyGlowing"));
        assertTrue(mixin.contains("getTeamColor"));
        assertTrue(mixin.contains("PlayerOutlineRenderer.shouldOutline"));
        assertTrue(mixin.contains("PlayerOutlineRenderer.controlsOutline"));
        assertTrue(mixin.contains("PlayerOutlineRenderer.getOutlineColor"));
        assertTrue(mixin.contains("cir.setReturnValue(PlayerOutlineRenderer.shouldOutline(entity));"));
    }

    @Test
    void spectatorGlowManagerDoesNotWriteVanillaGlowingTag() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/spec/SpectatorGlowManager.java"));

        assertFalse(manager.contains("setGlowingTag"));
    }
}
