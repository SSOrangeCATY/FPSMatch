package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.compat.PassThroughFlagResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassThroughFallbackStructureTest {

    @Test
    void flagsMergePreservesAnyTruePassThroughSignal() {
        PassThroughFlagResolver.Flags smoke = new PassThroughFlagResolver.Flags(false, true, false);
        PassThroughFlagResolver.Flags wall = new PassThroughFlagResolver.Flags(true, false, true);

        PassThroughFlagResolver.Flags merged = smoke.or(wall);

        assertTrue(merged.passWall());
        assertTrue(merged.passSmoke());
        assertTrue(merged.scoped());
        assertFalse(PassThroughFlagResolver.Flags.NONE.passWall());
    }

    @Test
    void taczHurtBridgeCarriesBulletForRecentHitFallback() throws IOException {
        String event = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMGunDamageEvent.java"));
        assertTrue(event.contains("private final Entity bullet;"));
        assertTrue(event.contains("@Nullable public Entity getBullet()"));

        String bridge = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/compat/tacz/TACZGunEventBridge.java"));
        assertTrue(bridge.contains("PassThroughFlagResolver.markSmokeIfNeeded(event.getBullet(), hurtEntity);"));
        assertTrue(bridge.contains("event.isHeadShot(), event.getBullet()"));
    }

    @Test
    void deathPipelineFallsBackToRecentPassThroughFlags() throws IOException {
        String hook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMDeathPipelineEventHook.java"));

        assertTrue(hook.contains("PassThroughFlagResolver.fromBulletAndTarget("));
        assertTrue(hook.contains("event.getBullet(),"));
        assertTrue(hook.contains("passThroughFlags.passWall()"));
        assertTrue(hook.contains("passThroughFlags.passSmoke()"));
        assertTrue(hook.contains("context.setPassWall(context.isPassWall() || recentGunHit.passWall());"));
        assertTrue(hook.contains("context.setPassSmoke(context.isPassSmoke() || recentGunHit.passSmoke());"));
        assertTrue(hook.contains("context.setPassWall(context.isPassWall() || gunKill.passWall());"));
        assertTrue(hook.contains("context.setPassSmoke(context.isPassSmoke() || gunKill.passSmoke());"));
    }
}
