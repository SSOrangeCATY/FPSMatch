package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarkerResetAccumulator {
    private final int maxAssemblies;
    private final int maxPagesPerReset;
    private final int maxMarkersPerReset;
    private final long ttlMillis;
    private final Map<ResetKey, Assembly> assemblies = new LinkedHashMap<>();
    private final Map<ResetKey, Long> completed = new LinkedHashMap<>();

    public MarkerResetAccumulator(
            int maxAssemblies,
            int maxPagesPerReset,
            int maxMarkersPerReset,
            long ttlMillis
    ) {
        if (maxAssemblies <= 0 || maxPagesPerReset <= 0
                || maxMarkersPerReset <= 0 || ttlMillis <= 0) {
            throw new IllegalArgumentException("Marker reset limits must be positive");
        }
        this.maxAssemblies = maxAssemblies;
        this.maxPagesPerReset = maxPagesPerReset;
        this.maxMarkersPerReset = maxMarkersPerReset;
        this.ttlMillis = ttlMillis;
    }

    public synchronized Optional<List<WireMarker.Marker>> accept(
            MarkerWireMessage.Reset page,
            long nowMillis
    ) {
        Objects.requireNonNull(page, "page");
        discardExpired(nowMillis);
        if (page.pageCount() > maxPagesPerReset) {
            throw new IllegalArgumentException("Marker reset page count exceeds its limit");
        }
        ResetKey key = ResetKey.of(page);
        if (completed.containsKey(key)) {
            return Optional.empty();
        }
        Assembly assembly = assemblies.get(key);
        if (assembly == null) {
            if (assemblies.size() >= maxAssemblies) {
                throw new IllegalArgumentException("Marker reset assembly limit exceeded");
            }
            assembly = new Assembly(page.pageCount(), nowMillis);
            assemblies.put(key, assembly);
        } else if (assembly.pages.length != page.pageCount()) {
            assemblies.remove(key);
            throw new IllegalArgumentException("Marker reset page count changed");
        }

        List<WireMarker.Marker> canonicalPage = List.copyOf(page.markers());
        List<WireMarker.Marker> existing = assembly.pages[page.pageIndex()];
        if (existing != null) {
            if (existing.equals(canonicalPage)) {
                return Optional.empty();
            }
            assemblies.remove(key);
            throw new IllegalArgumentException("Marker reset duplicate page conflict");
        }
        if ((long) assembly.markerCount + canonicalPage.size() > maxMarkersPerReset) {
            assemblies.remove(key);
            throw new IllegalArgumentException("Marker reset marker count exceeds its limit");
        }
        assembly.pages[page.pageIndex()] = canonicalPage;
        assembly.receivedPages++;
        assembly.markerCount += canonicalPage.size();
        assembly.lastProgressMillis = nowMillis;
        if (assembly.receivedPages < assembly.pages.length) {
            return Optional.empty();
        }

        ArrayList<WireMarker.Marker> markers = new ArrayList<>(assembly.markerCount);
        for (List<WireMarker.Marker> markersPage : assembly.pages) {
            if (markersPage == null) {
                assemblies.remove(key);
                throw new IllegalArgumentException("Marker reset is missing a page");
            }
            markers.addAll(markersPage);
        }
        assemblies.remove(key);
        completed.put(key, nowMillis);
        trimCompleted();
        return Optional.of(List.copyOf(markers));
    }

    public synchronized int discardExpired(long nowMillis) {
        int removed = 0;
        Iterator<Map.Entry<ResetKey, Assembly>> iterator = assemblies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResetKey, Assembly> entry = iterator.next();
            if (nowMillis - entry.getValue().lastProgressMillis > ttlMillis) {
                iterator.remove();
                removed++;
            }
        }
        completed.entrySet().removeIf(entry -> nowMillis - entry.getValue() > ttlMillis);
        return removed;
    }

    public synchronized void clear() {
        assemblies.clear();
        completed.clear();
    }

    private void trimCompleted() {
        Iterator<ResetKey> iterator = completed.keySet().iterator();
        while (completed.size() > maxAssemblies && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record ResetKey(
            WireIdentity.ScopeLease lease,
            WireIdentity.RuntimeIdentity runtime,
            UUID streamEpoch,
            UUID resetId
    ) {
        private static ResetKey of(MarkerWireMessage.Reset page) {
            return new ResetKey(
                    page.lease(), page.runtime(), page.streamEpoch(), page.resetId()
            );
        }
    }

    private static final class Assembly {
        private final List<WireMarker.Marker>[] pages;
        private int receivedPages;
        private int markerCount;
        private long lastProgressMillis;

        @SuppressWarnings("unchecked")
        private Assembly(int pageCount, long nowMillis) {
            this.pages = (List<WireMarker.Marker>[]) new List<?>[pageCount];
            this.lastProgressMillis = nowMillis;
        }

        @Override
        public String toString() {
            return "Assembly[pages=" + Arrays.toString(pages) + "]";
        }
    }
}
