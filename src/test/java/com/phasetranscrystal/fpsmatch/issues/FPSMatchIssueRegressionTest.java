package com.phasetranscrystal.fpsmatch.issues;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FPSMatchIssueRegressionTest {

    @Test
    void setTempDamageAssignsTheMethodParameter() throws IOException {
        String playerData = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/data/PlayerData.java"));
        String setTempDamage = playerData.substring(playerData.indexOf("public void setTempDamage"), playerData.indexOf("public void addScore"));

        assertTrue(setTempDamage.contains("this._damage = tempDamage;"));
        assertFalse(setTempDamage.contains("this._damage = damage;"));
    }

    @Test
    void clientTeamLookupDoesNotReportEveryCachedPlayerAsCsdm() throws IOException {
        String clientData = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/data/FPSMClientGlobalData.java"));

        assertFalse(clientData.contains("new PlayerTeamData(\"csdm\", cached)"));
        assertFalse(clientData.contains("playerDataCache.put(uuid, data);"));
        assertTrue(clientData.contains("new PlayerTeamData(teamName, data)"));
    }

    @Test
    void asyncPersistenceUsesSharedExecutorInsteadOfPerCallThreadPools() throws IOException {
        String dataManager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/persistence/FPSMDataManager.java"));

        assertFalse(dataManager.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(dataManager.contains("ASYNC_EXECUTOR"));
    }

    @Test
    void headshotKillsUseRoundTempField() throws IOException {
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
    void respawnEventIsSubscribedAndRestoresLivingState() throws IOException {
        String eventHook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMEventHook.java"));
        String respawnHandler = eventHook.substring(eventHook.indexOf("public static void onPlayerRespawnEvent"));

        assertTrue(eventHook.contains("@SubscribeEvent(priority = EventPriority.LOWEST)\n    public static void onPlayerRespawnEvent"));
        assertTrue(respawnHandler.contains("data.setLiving(true)"));
        assertFalse(respawnHandler.contains("TODO"));
    }

    @Test
    void shopEditorSlotsUseCenteredGridOffsets() throws IOException {
        String container = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditorShopContainer.java"));
        String screen = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditorShopScreen.java"));

        assertFalse(container.contains("int start = 5;"));
        assertTrue(container.contains("getGridLeft() + col * SLOT_SPACING_X"));
        assertTrue(container.contains("getGridTop() + row * SLOT_SPACING_Y"));
        assertTrue(screen.contains("this.leftPos = (this.width - this.imageWidth) / 2;"));
        assertFalse(screen.contains("this.imageWidth = this.width;"));
    }
}
