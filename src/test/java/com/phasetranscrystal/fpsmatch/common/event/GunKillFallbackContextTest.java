package com.phasetranscrystal.fpsmatch.common.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GunKillFallbackContextTest {

    @Test
    void fallbackCreatedContextAppliesGunKillDetailBeforePendingDeathIsReturned() throws IOException {
        Path source = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMDeathPipelineEventHook.java");
        String code = Files.readString(source);
        String fallbackBranch = code.substring(
                code.indexOf("readyDeaths.computeIfAbsent"),
                code.indexOf("return new PendingDeath(map, context);")
        );

        assertTrue(fallbackBranch.contains("applyGunKillDetail(context, detail);"));
    }
}
