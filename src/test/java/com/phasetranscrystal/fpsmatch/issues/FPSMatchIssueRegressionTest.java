package com.phasetranscrystal.fpsmatch.issues;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FPSMatchIssueRegressionTest {

    @Test
    void asyncPersistenceUsesSharedExecutorInsteadOfPerCallThreadPools() throws IOException {
        String dataManager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/persistence/FPSMDataManager.java"));

        assertFalse(dataManager.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(dataManager.contains("ASYNC_EXECUTOR"));
    }

    @Test
    void shopEditorSlotsUseSharedCenteredGridConstants() throws IOException {
        String container = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditorShopContainer.java"));
        String screen = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditorShopScreen.java"));

        assertFalse(container.contains("int start = 5;"));
        assertTrue(container.contains("getGridLeft() + col * SLOT_SPACING_X"));
        assertTrue(container.contains("getGridTop() + row * SLOT_SPACING_Y"));
        assertTrue(screen.contains("leftPos + imageWidth / 2"));
        assertTrue(screen.contains("topPos + imageHeight - 30"));
    }

    @Test
    void respawnEventIsRegisteredAndRestoresMapPlayerState() throws IOException {
        String eventHook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMEventHook.java"));
        String baseMap = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/map/BaseMap.java"));
        String respawnHandler = eventHook.substring(eventHook.indexOf("onPlayerRespawnEvent"));

        assertTrue(eventHook.contains("@SubscribeEvent(priority = EventPriority.LOWEST)\n    public static void onPlayerRespawnEvent"));
        assertTrue(respawnHandler.contains("map.handleRespawn(player)"));
        assertTrue(baseMap.contains("public void handleRespawn(ServerPlayer player)"));
        String baseRespawn = baseMap.substring(baseMap.indexOf("public void handleRespawn"), baseMap.indexOf("public boolean teleportToPoint"));
        assertTrue(baseRespawn.contains("data.setLiving(true)"));
        assertTrue(baseRespawn.contains("teleportPlayerToReSpawnPoint(player)"));
    }

    @Test
    void suicideGunKillCannotKeepHeadshotFlag() throws IOException {
        String eventHook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMDeathPipelineEventHook.java"));
        String finalizeDeath = eventHook.substring(eventHook.indexOf("private static void finalizeDeath"));

        assertTrue(finalizeDeath.contains("boolean selfKill"));
        assertTrue(finalizeDeath.contains("context.setHeadShot(gunKill.isHeadShot() && !selfKill);"));
    }

    @Test
    void headshotKillsUseRoundTemporaryStorageWhenRoundsAreEnabled() throws IOException {
        String playerData = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/data/PlayerData.java"));
        String addHeadshotKill = playerData.substring(playerData.indexOf("public void addHeadshotKill"), playerData.indexOf("public void setHeadshotKills"));
        String saveRoundData = playerData.substring(playerData.indexOf("public void saveRoundData"), playerData.indexOf("public void reset()"));

        assertTrue(playerData.contains("private int _headshotKills"));
        assertTrue(playerData.contains("public int getTempHeadshotKills()"));
        assertTrue(addHeadshotKill.contains("_headshotKills++"));
        assertTrue(saveRoundData.contains("this.headshotKills += _headshotKills;"));
        assertTrue(saveRoundData.contains("this._headshotKills = 0;"));
    }

    @Test
    void suicideDeathContextCannotRemainHeadshot() throws IOException {
        String deathContext = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/map/DeathContext.java"));

        assertTrue(deathContext.contains("public boolean isSuicide()"));
        assertTrue(deathContext.contains("this.headShot = headShot && !isSuicide();"));
        assertTrue(deathContext.contains("if (isSuicide()) {"));
        assertTrue(deathContext.contains("this.headShot = false;"));
    }

    @Test
    void soundEnginePlayMixinUsesReturnableCallback() throws IOException {
        String soundMixin = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/sound/SoundEngineMixin.java"));

        assertFalse(soundMixin.contains("onPlaySound(SoundInstance sound, CallbackInfo ci)"));
        assertTrue(soundMixin.contains("onPlaySound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir)"));
    }

    @Test
    void capabilityMapDoesNotRegisterPlainCapabilitiesAsEventListeners() throws IOException {
        String capabilityMap = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/capability/CapabilityMap.java"));

        assertFalse(capabilityMap.contains("NeoForge.EVENT_BUS.register(capability)"));
        assertFalse(capabilityMap.contains("NeoForge.EVENT_BUS.unregister(cap)"));
    }

    @Test
    void mapSelectionScreensDoNotRequestSecondBlurPass() throws IOException {
        for (String path : java.util.List.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapSelectionScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapDetailScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapManageScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapSettingsScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapShopScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapInviteScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/mapselect/FPSMMapInvitationScreen.java",
                "src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/FPSMTeamManageScreen.java"
        )) {
            String source = Files.readString(Path.of(path));

            assertFalse(source.contains("extractBackground("), path);
        }
    }
}
