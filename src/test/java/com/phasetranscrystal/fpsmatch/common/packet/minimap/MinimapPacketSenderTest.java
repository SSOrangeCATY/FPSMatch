package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.EditorWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireError;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireEditor;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapPacketSenderTest {

    @Test
    void c2sFrameIsSentAsAnOrderedSequenceOfIndividualPackets() {
        UUID frameId = new UUID(0, 200);
        EditorWireMessage.UploadFragment message = uploadFragment(40_000);
        List<MinimapC2SPacket> sent = new ArrayList<>();

        int count = MinimapPacketSender.sendC2S(
                frameId, message, sent::add
        );

        assertTrue(count > 1);
        assertEquals(count, sent.size());
        List<MinimapFrameSegment> expected = MinimapC2SPacket.fromMessage(
                frameId, message
        ).stream().map(MinimapC2SPacket::segment).toList();
        assertEquals(expected, sent.stream()
                .map(MinimapC2SPacket::segment)
                .toList());
        for (int index = 0; index < sent.size(); index++) {
            assertEquals(index, sent.get(index).segment().segmentIndex());
        }
    }

    @Test
    void wrongDirectionFailsBeforeTransportIsInvoked() {
        AtomicInteger sends = new AtomicInteger();
        PublishWireMessage.PublishReservation s2c =
                new PublishWireMessage.PublishReservation(
                        new UUID(0, 201), editorContext(), "token", 0, 1, 2
                );

        assertThrows(
                MinimapWireError.class,
                () -> MinimapPacketSender.sendC2S(
                        new UUID(0, 202),
                        s2c,
                        packet -> sends.incrementAndGet()
                )
        );
        assertEquals(0, sends.get());
    }

    private static EditorWireMessage.UploadFragment uploadFragment(int size) {
        byte[] data = new byte[size];
        Sha256 hash = Sha256Digest.of(data);
        return new EditorWireMessage.UploadFragment(
                new UUID(0, 203),
                editorContext(),
                new WireEditor.UploadData(new WireTransfer.TransferFragment(
                        new UUID(0, 204),
                        0,
                        1,
                        size,
                        hash,
                        hash,
                        data
                ))
        );
    }

    private static WireIdentity.EditorContext editorContext() {
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(
                        WireIdentity.Scope.EDITOR, 1, 2
                ),
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("g", "m"),
                                NamespacedId.parse("a:d")
                        ),
                        NamespacedId.parse("a:o")
                ),
                new UUID(0, 205),
                new UUID(0, 206),
                0,
                new Sha256("11".repeat(32)),
                new Sha256("22".repeat(32)),
                0
        );
    }
}
