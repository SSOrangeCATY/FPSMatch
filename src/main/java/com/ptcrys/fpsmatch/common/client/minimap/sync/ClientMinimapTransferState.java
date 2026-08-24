package com.ptcrys.fpsmatch.common.client.minimap.sync;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ClientMinimapTransferState {
    private ClientMinimapTransferState() {
    }

    record PendingSubscribe(
            RuntimeWireMessage.Subscribe subscribe,
            List<ContainerPath> requiredPaths,
            long expiresAtMillis
    ) {
    }

    static final class ActiveScope {
        private final UUID subscribeRequestId;
        private final WireIdentity.ScopeLease lease;
        private final WireIdentity.RuntimeIdentity identity;
        private final RuntimeGeneration generation;
        private List<ContainerPath> requiredPaths;
        private long manifestDeadlineMillis;
        private boolean manifestComplete;
        private boolean activated;

        ActiveScope(
                UUID subscribeRequestId,
                WireIdentity.ScopeLease lease,
                WireIdentity.RuntimeIdentity identity,
                RuntimeGeneration generation,
                List<ContainerPath> requiredPaths,
                long manifestDeadlineMillis
        ) {
            this.subscribeRequestId = subscribeRequestId;
            this.lease = lease;
            this.identity = identity;
            this.generation = generation;
            this.requiredPaths = requiredPaths;
            this.manifestDeadlineMillis = manifestDeadlineMillis;
        }

        UUID subscribeRequestId() {
            return subscribeRequestId;
        }

        WireIdentity.ScopeLease lease() {
            return lease;
        }

        WireIdentity.RuntimeIdentity identity() {
            return identity;
        }

        RuntimeGeneration generation() {
            return generation;
        }

        List<ContainerPath> requiredPaths() {
            return requiredPaths;
        }

        long manifestDeadlineMillis() {
            return manifestDeadlineMillis;
        }

        boolean manifestComplete() {
            return manifestComplete;
        }

        boolean activated() {
            return activated;
        }

        void refreshManifestDeadline(long deadlineMillis) {
            manifestDeadlineMillis = deadlineMillis;
        }

        void manifestComplete(List<ContainerPath> activationPaths) {
            requiredPaths = List.copyOf(activationPaths);
            manifestComplete = true;
        }

        void markActivated() {
            activated = true;
        }
    }

    static final class PendingEntryRequest {
        private final PendingEntryTransfer transfer;
        private final Map<ContainerPath, RuntimeEntryDescriptor> remaining;
        private long lastProgressMillis;

        PendingEntryRequest(
                PendingEntryTransfer transfer,
                Map<ContainerPath, RuntimeEntryDescriptor> remaining,
                long lastProgressMillis
        ) {
            this.transfer = transfer;
            this.remaining = remaining;
            this.lastProgressMillis = lastProgressMillis;
        }

        PendingEntryTransfer transfer() {
            return transfer;
        }

        Map<ContainerPath, RuntimeEntryDescriptor> remaining() {
            return remaining;
        }

        long lastProgressMillis() {
            return lastProgressMillis;
        }

        void progressed(long nowMillis) {
            lastProgressMillis = nowMillis;
        }
    }

    record PendingEntryTransfer(
            ActiveScope scope,
            List<ContainerPath> requiredPaths,
            Set<UUID> requestIds
    ) {
    }
}
