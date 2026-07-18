package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorAckCursorTest {
    @Test
    void pendingQueueIsIdempotentAndRejectsGaps() {
        PendingOperationQueue queue = new PendingOperationQueue();
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EditorCommand one = command(1, "a");
        EditorCommand two = command(2, "b");
        queue.enqueue(draftId, one);
        queue.enqueue(draftId, two);
        queue.enqueue(draftId, one); // duplicate
        assertEquals(List.of(1L, 2L), queue.pendingSequences(draftId));

        assertThrows(EditorCommandException.class, () -> queue.ack(draftId, 2, hash("x")));
        DraftSnapshot snapshot = queue.ack(draftId, 1, hash("root1"));
        assertEquals(1L, snapshot.ackCursor());
        assertEquals(hash("root1"), snapshot.draftRootHash());
        assertEquals(List.of(2L), queue.pendingSequences(draftId));

        assertThrows(EditorCommandException.class, () -> queue.enqueue(draftId, command(4, "gap")));
        Optional<EditorCommand> resend = queue.nextResend(draftId);
        assertTrue(resend.isPresent());
        assertEquals(2L, resend.orElseThrow().sequence());
    }

    private static EditorCommand command(long sequence, String seed) {
        return new EditorCommand(
                sequence,
                hash("base"),
                hash(seed),
                EditorOperation.setOpacity("layer", 0.5)
        );
    }

    private static Sha256 hash(String seed) {
        return Sha256Digest.of(seed.getBytes(StandardCharsets.UTF_8));
    }
}
