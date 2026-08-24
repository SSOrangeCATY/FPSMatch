package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Makes capability visibility the authority boundary after repository commit. */
public final class MinimapBindingCoordinator {
    private final BindingStore store;
    private final Deque<RecoveryItem> pending = new ArrayDeque<>();
    private final Set<MapKey> pendingNotifications = new LinkedHashSet<>();
    private final Object recoveryCycle = new Object();

    public MinimapBindingCoordinator(BindingStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public synchronized MinimapCapability.Binding preflight(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long baseRevision
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        if (baseRevision < 0) {
            throw new IllegalArgumentException("Binding base revision must be non-negative");
        }
        Optional<MinimapCapability.Binding> current = store.read(mapKey);
        if (baseRevision == 0 && current.isEmpty()) {
            return null;
        }
        MinimapCapability.Binding binding = current.orElseThrow(() -> conflict(
                "Published minimap binding is unavailable"
        ));
        if (binding.revision() != baseRevision
                || !binding.dimension().equals(dimension)
                || !binding.documentId().equals(documentId)) {
            throw conflict("Published minimap binding changed");
        }
        return binding;
    }

    public synchronized boolean bindCommitted(
            MapKey mapKey,
            MinimapCapability.Binding expectedOld,
            MinimapCapability.Binding next
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(next, "next");
        Optional<MinimapCapability.Binding> current = store.read(mapKey);
        if (!current.equals(Optional.ofNullable(expectedOld))) {
            throw conflict("Published minimap binding changed before commit visibility");
        }
        try {
            store.write(mapKey, next);
            if (store.read(mapKey).filter(next::equals).isPresent()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // A committed repository revision stays hidden until exact binding readback succeeds.
        }
        restore(mapKey, expectedOld);
        pending.addLast(new RecoveryItem(mapKey, expectedOld, next));
        return false;
    }

    /** Returns the number of distinct map bindings made visible by this call. */
    public int recoverPending() {
        synchronized (recoveryCycle) {
            synchronized (this) {
                return recoverBindingsLocked();
            }
        }
    }

    /**
     * Returns newly visible map count; notification-only retries do not increment it.
     */
    public int recoverPending(Consumer<MapKey> recoveredBinding) {
        Objects.requireNonNull(recoveredBinding, "recoveredBinding");
        synchronized (recoveryCycle) {
            int recovered;
            List<MapKey> notifications;
            synchronized (this) {
                recovered = recoverBindingsLocked();
                notifications = List.copyOf(pendingNotifications);
            }
            Throwable failure = null;
            for (MapKey mapKey : notifications) {
                synchronized (this) {
                    pendingNotifications.remove(mapKey);
                }
                try {
                    recoveredBinding.accept(mapKey);
                } catch (RuntimeException | Error next) {
                    synchronized (this) {
                        pendingNotifications.add(mapKey);
                    }
                    if (failure == null) {
                        failure = next;
                    } else if (failure != next) {
                        failure.addSuppressed(next);
                    }
                }
            }
            if (failure instanceof RuntimeException next) {
                throw next;
            }
            if (failure instanceof Error next) {
                throw next;
            }
            return recovered;
        }
    }

    private int recoverBindingsLocked() {
        Set<MapKey> recovered = new LinkedHashSet<>();
        int attempts = pending.size();
        for (int index = 0; index < attempts; index++) {
            RecoveryItem item = pending.removeFirst();
            Optional<MinimapCapability.Binding> current = store.read(item.mapKey());
            if (current.filter(item.next()::equals).isPresent()) {
                recovered.add(item.mapKey());
                pendingNotifications.add(item.mapKey());
                continue;
            }
            if (!current.equals(Optional.ofNullable(item.expectedOld()))) {
                pending.addLast(item);
                continue;
            }
            boolean visible = false;
            try {
                store.write(item.mapKey(), item.next());
                visible = store.read(item.mapKey()).filter(item.next()::equals).isPresent();
            } catch (RuntimeException ignored) {
                // Retain the recovery item and old visible authority for the next replay.
            }
            if (visible) {
                recovered.add(item.mapKey());
                pendingNotifications.add(item.mapKey());
                continue;
            }
            restore(item.mapKey(), item.expectedOld());
            pending.addLast(item);
        }
        return recovered.size();
    }

    public synchronized int pendingRecoveryCount() {
        return pending.size();
    }

    public synchronized MinimapCapability.BindingClearResult compareAndClear(
            MapKey mapKey,
            MinimapCapability.Binding expected
    ) {
        return store.compareAndClear(
                Objects.requireNonNull(mapKey, "mapKey"),
                Objects.requireNonNull(expected, "expected")
        );
    }

    private void restore(MapKey mapKey, MinimapCapability.Binding previous) {
        try {
            if (previous == null) {
                store.clear(mapKey);
            } else {
                store.write(mapKey, previous);
            }
        } catch (RuntimeException ignored) {
            // The failed next binding is never trusted without an exact readback.
        }
    }

    private static SessionAccessException conflict(String message) {
        return new SessionAccessException(MinimapErrorCode.REVISION_CONFLICT, message);
    }

    public interface BindingStore {
        Optional<MinimapCapability.Binding> read(MapKey mapKey);

        void write(MapKey mapKey, MinimapCapability.Binding binding);

        void clear(MapKey mapKey);

        default MinimapCapability.BindingClearResult compareAndClear(
                MapKey mapKey,
                MinimapCapability.Binding expected
        ) {
            return MinimapCapability.BindingClearResult.UNAVAILABLE;
        }
    }

    private record RecoveryItem(
            MapKey mapKey,
            MinimapCapability.Binding expectedOld,
            MinimapCapability.Binding next
    ) {
    }
}
