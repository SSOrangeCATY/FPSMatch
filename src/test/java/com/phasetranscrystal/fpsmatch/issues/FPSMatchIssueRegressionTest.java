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
}
