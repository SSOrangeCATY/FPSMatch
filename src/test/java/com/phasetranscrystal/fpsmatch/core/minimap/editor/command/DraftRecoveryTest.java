package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftRecoveryTest {
    @Test
    void reconnectRestoresCommandLogAndKeepsDraftAfterSessionExpiry() {
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        Sha256 base = hash("base");
        EditorCommandLog log = EditorCommandLog.empty(base);
        log.append(EditorOperation.setOpacity("paint", 0.4));
        log.append(EditorOperation.setVisibility("paint", false));
        DraftSnapshot snapshot = DraftSnapshot.fromLog(draftId, 0L, base, log);
        assertEquals(2, snapshot.operations().size());

        EditorCommandLog restored = EditorCommandLog.restore(snapshot);
        assertEquals(snapshot.draftRootHash(), restored.rootHash());
        assertEquals(2, restored.currentState().operations().size());

        DraftLease lease = new DraftLease(draftId, Instant.parse("2026-01-01T00:00:00Z"), snapshot);
        assertTrue(lease.isExpired(Instant.parse("2026-01-01T00:10:01Z")));
        assertFalse(lease.snapshot().operations().isEmpty());
        assertEquals(snapshot.draftRootHash(), lease.snapshot().draftRootHash());
    }

    @Test
    void baseBlobPinIsRetainedForGc() {
        DraftAncestorPins pins = new InMemoryDraftAncestorPins();
        Sha256 base = hash("ancestor");
        pins.pin(base);
        assertTrue(pins.isPinned(base));
        pins.unpin(base);
        assertFalse(pins.isPinned(base));
    }

    private static Sha256 hash(String seed) {
        return Sha256Digest.of(seed.getBytes(StandardCharsets.UTF_8));
    }
}
