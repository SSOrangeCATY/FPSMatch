package com.phasetranscrystal.fpsmatch.core.team;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTeamsSourceGuardTest {
    @Test
    void roundMvpUsesCurrentRoundStats() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/team/MapTeams.java"));

        assertTrue(source.contains("data.getTempKills() * 3"));
        assertTrue(source.contains("data.getTempAssists()"));
        assertTrue(source.contains("data.getTempDamage() > 0"));
    }
}
