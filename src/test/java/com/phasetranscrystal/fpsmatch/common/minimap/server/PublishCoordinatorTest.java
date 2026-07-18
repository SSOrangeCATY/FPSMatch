package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishOutcome;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishTransaction;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapStorageFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishCoordinatorTest {
    private static final MapKey MAP = new MapKey("fpsmatch:test", "Test Map");

    @TempDir
    Path temporaryDirectory;

    @Test
    void responseFailureAfterCommitDoesNotSuppressBroadcastOrUndoTheRevision() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        RecordingDelivery delivery = new RecordingDelivery(true, false);
        PublishCoordinator coordinator = new PublishCoordinator(repository, delivery);
        PublishTransaction transaction = prepare(repository, 1);

        PublishDeliveryException failure = assertThrows(
                PublishDeliveryException.class,
                () -> coordinator.commit(transaction)
        );

        assertTrue(failure.outcome().committed());
        assertEquals(1, repository.current(MAP).orElseThrow().revision());
        assertEquals(List.of("response:1", "broadcast:1"), delivery.attempts);
    }

    @Test
    void broadcastFailureAfterCommitCanBeReplayedIdempotentlyDuringRecovery() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        RecordingDelivery delivery = new RecordingDelivery(false, true);
        PublishCoordinator coordinator = new PublishCoordinator(repository, delivery);
        PublishTransaction transaction = prepare(repository, 1);

        PublishDeliveryException failure = assertThrows(
                PublishDeliveryException.class,
                () -> coordinator.commit(transaction)
        );
        assertTrue(failure.outcome().committed());
        delivery.failBroadcast = false;

        assertTrue(coordinator.recoverAndReplay(MAP).committed());
        assertEquals(
                List.of("response:1", "broadcast:1", "broadcast:1"),
                delivery.attempts
        );
    }

    private static PublishTransaction prepare(MinimapRepository repository, long revision) {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(revision);
        return repository.prepare(
                repository.reserve(
                        MAP,
                        NamespacedId.parse("minecraft:overworld"),
                        NamespacedId.parse("fpsmatch:test-map"),
                        revision - 1
                ),
                pair.source(), pair.runtime()
        );
    }

    private static final class RecordingDelivery implements PublishDelivery {
        private final List<String> attempts = new ArrayList<>();
        private boolean failResponse;
        private boolean failBroadcast;

        private RecordingDelivery(boolean failResponse, boolean failBroadcast) {
            this.failResponse = failResponse;
            this.failBroadcast = failBroadcast;
        }

        @Override
        public void respond(PublishOutcome outcome) {
            attempts.add("response:" + outcome.revision());
            if (failResponse) {
                throw new IllegalStateException("response unavailable");
            }
        }

        @Override
        public void broadcastRevision(MapKey mapKey, long revision) {
            attempts.add("broadcast:" + revision);
            if (failBroadcast) {
                throw new IllegalStateException("broadcast unavailable");
            }
        }
    }
}
