package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapSyncManagerTest {
    private static final MapKey MAP_A = new MapKey("cs", "dust2");
    private static final MapKey MAP_B = new MapKey("cs", "inferno");
    private static final NamespacedId DOC = NamespacedId.parse("fpsmatch:dust2");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID PLAYER2 = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void rejectsSubscribeWhenCapabilityMissing() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> false, quotas());
        boolean accepted = manager.subscribe(
                PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1")
        );
        assertFalse(accepted);
        assertEquals(0, manager.subscriptionCount());
    }

    @Test
    void scopeCoexistenceAndIndependentUnsubscribe() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> true, quotas());
        assertTrue(manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1")));
        assertTrue(manager.subscribe(PLAYER, WireIdentity.Scope.TACTICAL_SCREEN, MAP_A, DOC, 1L, hash("r1")));
        assertTrue(manager.subscribe(PLAYER, WireIdentity.Scope.EDITOR, MAP_A, DOC, 1L, hash("r1")));
        assertEquals(3, manager.subscriptionCount(PLAYER));

        manager.unsubscribe(PLAYER, WireIdentity.Scope.TACTICAL_SCREEN, MAP_A);
        assertEquals(2, manager.subscriptionCount(PLAYER));
        assertTrue(manager.hasSubscription(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A));
        assertTrue(manager.hasSubscription(PLAYER, WireIdentity.Scope.EDITOR, MAP_A));
        assertFalse(manager.hasSubscription(PLAYER, WireIdentity.Scope.TACTICAL_SCREEN, MAP_A));
    }

    @Test
    void subscribeUnsubscribeAreIdempotent() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> true, quotas());
        assertTrue(manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1")));
        assertTrue(manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1")));
        assertEquals(1, manager.subscriptionCount(PLAYER));
        manager.unsubscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A);
        manager.unsubscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A);
        assertEquals(0, manager.subscriptionCount(PLAYER));
    }

    @Test
    void signatureOnlyManifestUpdatesAvoidNoopBroadcast() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> true, quotas());
        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1"));
        RuntimeManifestView first = new RuntimeManifestView(MAP_A, DOC, 1L, hash("r1"), "sig-1");
        assertEquals(1, manager.publishManifestIfChanged(first).size());
        assertTrue(manager.publishManifestIfChanged(first).isEmpty());
        RuntimeManifestView second = new RuntimeManifestView(MAP_A, DOC, 2L, hash("r2"), "sig-2");
        assertEquals(1, manager.publishManifestIfChanged(second).size());
    }

    @Test
    void enforcesPerPlayerFragmentQuotasAndFairScheduling() {
        MinimapSyncManager manager = new MinimapSyncManager(
                map -> true,
                new MinimapSyncQuotas(4, 2, 4)
        );
        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1"));
        manager.subscribe(PLAYER2, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1"));
        manager.enqueueFragment(PLAYER, MAP_A, WireIdentity.Scope.MATCH_HUD, fragment("a1"));
        manager.enqueueFragment(PLAYER, MAP_A, WireIdentity.Scope.MATCH_HUD, fragment("a2"));
        manager.enqueueFragment(PLAYER2, MAP_A, WireIdentity.Scope.MATCH_HUD, fragment("b1"));

        List<ScheduledFragment> firstTick = manager.scheduleFragments();
        assertEquals(2, firstTick.size()); // maxFragmentsPerTick=2, one each due to fairness
        assertEquals(1, firstTick.stream().filter(f -> f.playerId().equals(PLAYER)).count());
        assertEquals(1, firstTick.stream().filter(f -> f.playerId().equals(PLAYER2)).count());

        List<ScheduledFragment> secondTick = manager.scheduleFragments();
        assertEquals(1, secondTick.size());
        assertEquals(PLAYER, secondTick.get(0).playerId());
    }

    @Test
    void dirtyCoalescesAndLogoutMapSwitchCleanup() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> true, quotas());
        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1"));
        manager.markDirty(MAP_A, "section:0,0");
        manager.markDirty(MAP_A, "section:0,0");
        manager.markDirty(MAP_A, "section:1,0");
        assertEquals(2, manager.drainDirty(MAP_A).size());

        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_B, DOC, 1L, hash("rb"));
        manager.onPlayerLogout(PLAYER);
        assertEquals(0, manager.subscriptionCount(PLAYER));

        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1"));
        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_B, DOC, 1L, hash("rb"));
        manager.onMapSwitch(PLAYER, MAP_B);
        assertFalse(manager.hasSubscription(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A));
        assertTrue(manager.hasSubscription(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_B));
    }

    @Test
    void oldRevisionGraceRetainsPreviousRuntimeUntilExpired() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> true, quotas());
        manager.rememberPublished(MAP_A, 1L, hash("old"), 0L);
        manager.rememberPublished(MAP_A, 2L, hash("new"), 10L);
        assertTrue(manager.isWithinGrace(MAP_A, 1L, 15L, 20L));
        assertFalse(manager.isWithinGrace(MAP_A, 1L, 40L, 20L));
        assertTrue(manager.isWithinGrace(MAP_A, 2L, 40L, 20L));
    }

    @Test
    void tickDoesNotBroadcastWithoutSubscribersOrDirtySignature() {
        MinimapSyncManager manager = new MinimapSyncManager(map -> true, quotas());
        assertTrue(manager.tick(1L).isEmpty());
        manager.subscribe(PLAYER, WireIdentity.Scope.MATCH_HUD, MAP_A, DOC, 1L, hash("r1"));
        RuntimeManifestView view = new RuntimeManifestView(MAP_A, DOC, 1L, hash("r1"), "steady");
        manager.publishManifestIfChanged(view);
        assertTrue(manager.tick(2L).isEmpty());
    }

    private static MinimapSyncQuotas quotas() {
        return new MinimapSyncQuotas(8, 8, 4);
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static FragmentJob fragment(String id) {
        return new FragmentJob(id, 1, 1024);
    }
}