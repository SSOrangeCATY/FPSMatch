package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Dedicated minimap runtime sync manager.
 * Independent of {@code MapRoomSyncManager}; static runtime files never ride BaseMap.syncToClient().
 */
public final class MinimapSyncManager {
    private final Predicate<MapKey> capabilityPresent;
    private final MinimapSyncQuotas quotas;

    private final Map<SubscriptionKey, Subscription> subscriptions = new LinkedHashMap<>();
    private final Map<MapKey, String> lastManifestSignatures = new HashMap<>();
    private final Map<UUID, ArrayDeque<QueuedFragment>> fragmentQueues = new LinkedHashMap<>();
    private final Map<MapKey, LinkedHashSet<String>> dirtyKeys = new HashMap<>();
    private final Map<PublishedKey, Long> publishedAtMillis = new HashMap<>();
    private final Map<MapKey, Long> currentRevisions = new HashMap<>();

    public MinimapSyncManager(Predicate<MapKey> capabilityPresent, MinimapSyncQuotas quotas) {
        this.capabilityPresent = Objects.requireNonNull(capabilityPresent, "capabilityPresent");
        this.quotas = Objects.requireNonNull(quotas, "quotas");
    }

    public boolean subscribe(
            UUID playerId,
            WireIdentity.Scope scope,
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        if (!capabilityPresent.test(mapKey)) {
            return false;
        }
        SubscriptionKey key = new SubscriptionKey(playerId, scope, mapKey);
        if (subscriptions.containsKey(key)) {
            return true;
        }
        if (subscriptionCount(playerId) >= quotas.maxSubscriptionsPerPlayer()) {
            return false;
        }
        subscriptions.put(key, new Subscription(playerId, scope, mapKey, documentId, revision, runtimeHash));
        return true;
    }

    public void unsubscribe(UUID playerId, WireIdentity.Scope scope, MapKey mapKey) {
        subscriptions.remove(new SubscriptionKey(playerId, scope, mapKey));
        pruneEmptyFragmentQueue(playerId);
    }

    public boolean hasSubscription(UUID playerId, WireIdentity.Scope scope, MapKey mapKey) {
        return subscriptions.containsKey(new SubscriptionKey(playerId, scope, mapKey));
    }

    public int subscriptionCount() {
        return subscriptions.size();
    }

    public int subscriptionCount(UUID playerId) {
        int count = 0;
        for (Subscription subscription : subscriptions.values()) {
            if (subscription.playerId().equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    public List<UUID> publishManifestIfChanged(RuntimeManifestView view) {
        Objects.requireNonNull(view, "view");
        String previous = lastManifestSignatures.get(view.mapKey());
        if (view.signature().equals(previous)) {
            return List.of();
        }
        lastManifestSignatures.put(view.mapKey(), view.signature());
        currentRevisions.put(view.mapKey(), view.revision());
        rememberPublished(view.mapKey(), view.revision(), view.runtimeHash(), System.currentTimeMillis());
        List<UUID> recipients = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Subscription subscription : subscriptions.values()) {
            if (subscription.mapKey().equals(view.mapKey()) && seen.add(subscription.playerId())) {
                recipients.add(subscription.playerId());
            }
        }
        return List.copyOf(recipients);
    }

    public void enqueueFragment(
            UUID playerId,
            MapKey mapKey,
            WireIdentity.Scope scope,
            FragmentJob job
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(job, "job");
        if (!hasSubscription(playerId, scope, mapKey)) {
            return;
        }
        ArrayDeque<QueuedFragment> queue = fragmentQueues.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        if (queue.size() >= quotas.maxQueuedFragmentsPerPlayer()) {
            return;
        }
        queue.addLast(new QueuedFragment(mapKey, scope, job));
    }

    public List<ScheduledFragment> scheduleFragments() {
        if (fragmentQueues.isEmpty()) {
            return List.of();
        }
        List<ScheduledFragment> scheduled = new ArrayList<>();
        List<UUID> order = new ArrayList<>(fragmentQueues.keySet());
        int guard = 0;
        while (scheduled.size() < quotas.maxFragmentsPerTick() && !fragmentQueues.isEmpty()) {
            boolean progressed = false;
            for (UUID playerId : List.copyOf(order)) {
                if (scheduled.size() >= quotas.maxFragmentsPerTick()) {
                    break;
                }
                ArrayDeque<QueuedFragment> queue = fragmentQueues.get(playerId);
                if (queue == null || queue.isEmpty()) {
                    fragmentQueues.remove(playerId);
                    order.remove(playerId);
                    continue;
                }
                QueuedFragment next = queue.removeFirst();
                scheduled.add(new ScheduledFragment(playerId, next.mapKey(), next.scope(), next.job()));
                progressed = true;
                if (queue.isEmpty()) {
                    fragmentQueues.remove(playerId);
                    order.remove(playerId);
                }
            }
            if (!progressed) {
                break;
            }
            guard++;
            if (guard > quotas.maxFragmentsPerTick() + order.size() + 8) {
                break;
            }
        }
        return List.copyOf(scheduled);
    }
    public void markDirty(MapKey mapKey, String dirtyKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dirtyKey, "dirtyKey");
        dirtyKeys.computeIfAbsent(mapKey, ignored -> new LinkedHashSet<>()).add(dirtyKey);
    }

    public Set<String> drainDirty(MapKey mapKey) {
        LinkedHashSet<String> dirty = dirtyKeys.remove(mapKey);
        return dirty == null ? Set.of() : Set.copyOf(dirty);
    }

    public void onPlayerLogout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        subscriptions.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
        fragmentQueues.remove(playerId);
    }

    public void onMapSwitch(UUID playerId, MapKey remainingMap) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(remainingMap, "remainingMap");
        subscriptions.entrySet().removeIf(entry ->
                entry.getKey().playerId().equals(playerId)
                        && !entry.getKey().mapKey().equals(remainingMap)
        );
        ArrayDeque<QueuedFragment> queue = fragmentQueues.get(playerId);
        if (queue != null) {
            queue.removeIf(item -> !item.mapKey().equals(remainingMap));
            if (queue.isEmpty()) {
                fragmentQueues.remove(playerId);
            }
        }
    }

    public void rememberPublished(MapKey mapKey, long revision, Sha256 runtimeHash, long nowMillis) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        publishedAtMillis.put(new PublishedKey(mapKey, revision, runtimeHash), nowMillis);
        currentRevisions.put(mapKey, Math.max(currentRevisions.getOrDefault(mapKey, 0L), revision));
    }

    public boolean isWithinGrace(MapKey mapKey, long revision, long nowMillis, long graceMillis) {
        Long current = currentRevisions.get(mapKey);
        if (current != null && revision == current) {
            return true;
        }
        for (Map.Entry<PublishedKey, Long> entry : publishedAtMillis.entrySet()) {
            PublishedKey key = entry.getKey();
            if (key.mapKey().equals(mapKey) && key.revision() == revision) {
                return nowMillis - entry.getValue() <= graceMillis;
            }
        }
        return false;
    }

    /**
     * Optional periodic tick. Returns recipients only when something actually changed.
     * Empty subscribers or unchanged signatures produce no broadcast work.
     */
    public List<UUID> tick(long nowMillis) {
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        // Fragment scheduling is pull/schedule driven; tick itself never forces unconditional broadcast.
        return List.of();
    }

    private void pruneEmptyFragmentQueue(UUID playerId) {
        ArrayDeque<QueuedFragment> queue = fragmentQueues.get(playerId);
        if (queue != null && queue.isEmpty()) {
            fragmentQueues.remove(playerId);
        }
    }

    private record SubscriptionKey(UUID playerId, WireIdentity.Scope scope, MapKey mapKey) {
    }

    private record Subscription(
            UUID playerId,
            WireIdentity.Scope scope,
            MapKey mapKey,
            NamespacedId documentId,
            long revision,
            Sha256 runtimeHash
    ) {
    }

    private record QueuedFragment(MapKey mapKey, WireIdentity.Scope scope, FragmentJob job) {
    }

    private record PublishedKey(MapKey mapKey, long revision, Sha256 runtimeHash) {
    }
}