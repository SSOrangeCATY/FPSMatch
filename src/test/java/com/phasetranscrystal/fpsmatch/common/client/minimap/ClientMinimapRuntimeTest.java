package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMinimapRuntimeTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");
    private static final NamespacedId DOC = NamespacedId.parse("fpsmatch:dust2");

    @Test
    void loginLogoutAndMapDimensionRevisionBumpGeneration() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration first = runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        assertEquals(1L, first.connectionEpoch());
        assertTrue(runtime.isCurrent(first));

        RuntimeGeneration afterMap = runtime.switchMap(MAP, DOC, 2L, hash("r2"));
        assertFalse(runtime.isCurrent(first));
        assertTrue(runtime.isCurrent(afterMap));
        assertEquals(first.connectionEpoch(), afterMap.connectionEpoch());
        assertEquals(2L, afterMap.revision());

        RuntimeGeneration afterDim = runtime.switchDimension(NamespacedId.parse("minecraft:the_nether"));
        assertFalse(runtime.isCurrent(afterMap));
        assertTrue(runtime.isCurrent(afterDim));

        runtime.logout();
        assertFalse(runtime.isCurrent(afterDim));
        RuntimeGeneration secondLogin = runtime.login("server-b", MAP, DOC, 1L, hash("r1"));
        assertEquals(2L, secondLogin.connectionEpoch());
    }

    @Test
    void connectionCanAcquirePendingScopeBeforeFirstRuntimeAck() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();

        runtime.connect("server-a");
        MinimapScopeLease pending = runtime.acquirePending(WireIdentity.Scope.MATCH_HUD);

        assertTrue(runtime.currentGeneration().isEmpty());
        assertTrue(runtime.isPending(pending));

        RuntimeGeneration acknowledged = runtime.acknowledge(
                pending,
                new WireIdentity.RuntimeIdentity(
                        new WireIdentity.DocumentBinding(
                                new WireIdentity.MapTarget(
                                        MAP,
                                        NamespacedId.parse("minecraft:overworld")
                                ),
                                DOC
                        ),
                        1L,
                        hash("r1"),
                        java.util.Optional.empty()
                )
        ).orElseThrow();

        assertTrue(runtime.isCurrent(acknowledged));
        assertTrue(runtime.canCommit(acknowledged, pending));
    }

    @Test
    void staleOrMismatchedScopeAckCannotEstablishRuntime() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.connect("server-a");
        MinimapScopeLease first = runtime.acquirePending(WireIdentity.Scope.MATCH_HUD);
        runtime.release(WireIdentity.Scope.MATCH_HUD);
        MinimapScopeLease second = runtime.acquirePending(WireIdentity.Scope.MATCH_HUD);

        WireIdentity.RuntimeIdentity identity = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                MAP,
                                NamespacedId.parse("minecraft:overworld")
                        ),
                        DOC
                ),
                1L,
                hash("r1"),
                java.util.Optional.empty()
        );

        assertTrue(runtime.acknowledge(first, identity).isEmpty());
        assertTrue(runtime.acknowledge(
                new MinimapScopeLease(
                        WireIdentity.Scope.TACTICAL_SCREEN,
                        second.scopeEpoch(),
                        second.runtimeGeneration()
                ),
                identity
        ).isEmpty());
        assertTrue(runtime.currentGeneration().isEmpty());
    }

    @Test
    void additionalScopeAckCanJoinTheEstablishedRuntimeIdentity() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.connect("server-a");
        MinimapScopeLease hud = runtime.acquirePending(WireIdentity.Scope.MATCH_HUD);
        WireIdentity.RuntimeIdentity identity = identity(1L, hash("r1"));
        RuntimeGeneration generation = runtime.acknowledge(hud, identity).orElseThrow();

        MinimapScopeLease tactical = runtime.acquirePending(
                WireIdentity.Scope.TACTICAL_SCREEN
        );

        assertFalse(runtime.canCommit(generation, tactical));
        assertEquals(
                generation,
                runtime.acknowledge(tactical, identity).orElseThrow()
        );
        assertTrue(runtime.canCommit(generation, hud));
        assertTrue(runtime.canCommit(generation, tactical));
        assertTrue(runtime.acknowledge(
                tactical,
                identity(2L, hash("r2"))
        ).isEmpty());
    }

    @Test
    void pendingTransitionKeepsOldIdentityReadableButOnlyNewAckCanCommit() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration old = runtime.login(
                "server-a", MAP, DOC, 1L, hash("r1")
        );
        MinimapScopeLease oldHud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);

        runtime.beginTransition();
        MinimapScopeLease pending = runtime.acquirePending(
                WireIdentity.Scope.MATCH_HUD
        );

        assertEquals(old, runtime.currentGeneration().orElseThrow());
        assertFalse(runtime.canCommit(old, oldHud));
        assertTrue(runtime.isPending(pending));
        assertTrue(pending.runtimeGeneration() > old.localGeneration());

        WireIdentity.RuntimeIdentity nextIdentity = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("cs", "inferno"),
                                NamespacedId.parse("minecraft:the_nether")
                        ),
                        NamespacedId.parse("fpsmatch:inferno")
                ),
                2L,
                hash("r2"),
                java.util.Optional.empty()
        );
        RuntimeGeneration next = runtime.acknowledge(
                pending, nextIdentity
        ).orElseThrow();

        assertFalse(runtime.isCurrent(old));
        assertTrue(runtime.isCurrent(next));
        assertTrue(runtime.canCommit(next, pending));
    }

    @Test
    void concurrentHudTacticalAndEditorLeasesAreIndependent() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration generation = runtime.login("server-a", MAP, DOC, 3L, hash("r3"));
        MinimapScopeLease hud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        MinimapScopeLease tactical = runtime.acquire(WireIdentity.Scope.TACTICAL_SCREEN);
        MinimapScopeLease editor = runtime.acquire(WireIdentity.Scope.EDITOR);
        assertTrue(runtime.canCommit(generation, hud));
        assertTrue(runtime.canCommit(generation, tactical));
        assertTrue(runtime.canCommit(generation, editor));

        runtime.release(WireIdentity.Scope.TACTICAL_SCREEN);
        assertFalse(runtime.canCommit(generation, tactical));
        assertTrue(runtime.canCommit(generation, hud));
        assertTrue(runtime.canCommit(generation, editor));
    }

    @Test
    void reopeningScopeAdvancesEpochAndRejectsOldLease() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration generation = runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease first = runtime.acquire(WireIdentity.Scope.TACTICAL_SCREEN);
        runtime.release(WireIdentity.Scope.TACTICAL_SCREEN);
        MinimapScopeLease second = runtime.acquire(WireIdentity.Scope.TACTICAL_SCREEN);
        assertTrue(second.scopeEpoch() > first.scopeEpoch());
        assertFalse(runtime.canCommit(generation, first));
        assertTrue(runtime.canCommit(generation, second));
    }

    @Test
    void lateCallbackCasRequiresCurrentGenerationAndAtLeastOneValidLease() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration generation = runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease hud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        MinimapScopeLease tactical = runtime.acquire(WireIdentity.Scope.TACTICAL_SCREEN);

        AtomicBoolean committed = new AtomicBoolean();
        assertTrue(runtime.commitIfCurrent(generation, new MinimapScopeLease[] {hud, tactical}, () -> committed.set(true)));
        assertTrue(committed.get());

        runtime.release(WireIdentity.Scope.TACTICAL_SCREEN);
        AtomicBoolean rejected = new AtomicBoolean();
        assertFalse(runtime.commitIfCurrent(generation, new MinimapScopeLease[] {tactical}, () -> rejected.set(true)));
        assertFalse(rejected.get());
        assertTrue(runtime.commitIfCurrent(generation, new MinimapScopeLease[] {hud, tactical}, () -> {}));
    }

    @Test
    void screenOnlyCallbackRequiresItsOriginalLease() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration generation = runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease tactical = runtime.acquire(WireIdentity.Scope.TACTICAL_SCREEN);
        runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        runtime.release(WireIdentity.Scope.TACTICAL_SCREEN);

        AtomicBoolean ran = new AtomicBoolean();
        assertFalse(runtime.commitScreenIfCurrent(
                generation, WireIdentity.Scope.TACTICAL_SCREEN, tactical, () -> ran.set(true)
        ));
        assertFalse(ran.get());
    }

    @Test
    void closingTacticalDoesNotCancelHudSharedTask() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration generation = runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease hud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        MinimapScopeLease tactical = runtime.acquire(WireIdentity.Scope.TACTICAL_SCREEN);
        runtime.release(WireIdentity.Scope.TACTICAL_SCREEN);
        AtomicBoolean committed = new AtomicBoolean();
        assertTrue(runtime.commitIfCurrent(generation, new MinimapScopeLease[] {hud, tactical}, () -> committed.set(true)));
        assertTrue(committed.get());
    }

    @Test
    void reloadAndResetInvalidateAllLeasesAndGeneration() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        RuntimeGeneration generation = runtime.login("server-a", MAP, DOC, 1L, hash("r1"));
        MinimapScopeLease hud = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        runtime.reloadResources();
        assertFalse(runtime.isCurrent(generation));
        assertFalse(runtime.canCommit(generation, hud));

        RuntimeGeneration afterReload = runtime.currentGeneration().orElseThrow();
        MinimapScopeLease hud2 = runtime.acquire(WireIdentity.Scope.MATCH_HUD);
        runtime.resetAll();
        assertFalse(runtime.isCurrent(afterReload));
        assertFalse(runtime.canCommit(afterReload, hud2));
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static WireIdentity.RuntimeIdentity identity(long revision, Sha256 runtimeHash) {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                MAP,
                                NamespacedId.parse("minecraft:overworld")
                        ),
                        DOC
                ),
                revision,
                runtimeHash,
                java.util.Optional.empty()
        );
    }
}
