package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeWireMessageTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void subscribeMatchesTypedGolden() {
        RuntimeWireMessage.Subscribe subscribe = new RuntimeWireMessage.Subscribe(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 1, 2),
                new WireIdentity.MapTarget(
                        new MapKey("fpsmatch:test", "Map A"),
                        NamespacedId.parse("minecraft:overworld")
                ),
                Optional.of(new WireIdentity.RuntimeHint(
                        NamespacedId.parse("fpsmatch:test_map"),
                        3,
                        new Sha256("11".repeat(32))
                ))
        );

        assertArrayEquals(hex(
                "0100016f"
                        + "00000000000000000000000000000001"
                        + "000102"
                        + "0d6670736d617463683a74657374"
                        + "054d61702041"
                        + "096d696e656372616674096f766572776f726c64"
                        + "01"
                        + "086670736d6174636808746573745f6d6170"
                        + "03"
                        + "11".repeat(32)
        ), MinimapWireCodec.encode(subscribe));
    }

    @Test
    void unsubscribeMatchesTypedGolden() {
        RuntimeWireMessage.Unsubscribe unsubscribe = new RuntimeWireMessage.Unsubscribe(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 3, 4),
                new WireIdentity.MapTarget(
                        new MapKey("fpsmatch:test", "Map A"),
                        NamespacedId.parse("minecraft:overworld")
                )
        );

        assertArrayEquals(hex(
                "0100023b"
                        + "00000000000000000000000000000002"
                        + "010304"
                        + "0d6670736d617463683a74657374"
                        + "054d61702041"
                        + "096d696e656372616674096f766572776f726c64"
        ), MinimapWireCodec.encode(unsubscribe));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.Unsubscribe(
                UUID.randomUUID(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 0, 0),
                unsubscribe.target()
        ));
    }

    @Test
    void requestEntriesMatchesTypedGoldenAndBoundsEntryCount() {
        WireIdentity.RuntimeIdentity runtime = runtimeIdentity();
        WireTransfer.EntryRequest entry = new WireTransfer.EntryRequest(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("a.json"),
                new Sha256("44".repeat(32))
        );
        RuntimeWireMessage.RequestEntries request = new RuntimeWireMessage.RequestEntries(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 5, 6),
                runtime,
                List.of(entry)
        );

        assertArrayEquals(hex(
                "0100039701"
                        + "00000000000000000000000000000003"
                        + "000506"
                        + targetHex()
                        + "086670736d6174636808746573745f6d6170"
                        + "07" + "22".repeat(32) + "00"
                        + "01"
                        + "06612e6a736f6e" + "44".repeat(32)
        ), MinimapWireCodec.encode(request));

        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.RequestEntries(
                request.requestId(), request.lease(), runtime,
                java.util.Collections.nCopies(257, entry)
        ));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.RequestEntries(
                request.requestId(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 0, 0),
                runtime,
                List.of(entry)
        ));
    }

    @Test
    void requestMarkerResetMatchesTypedGoldenWithGroupedOptionalHint() {
        RuntimeWireMessage.RequestMarkerReset withoutHint =
                new RuntimeWireMessage.RequestMarkerReset(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 8, 9),
                        runtimeIdentity(),
                        Optional.empty()
                );

        assertArrayEquals(hex(
                "01000470"
                        + "00000000000000000000000000000004"
                        + "000809"
                        + targetHex()
                        + "086670736d6174636808746573745f6d6170"
                        + "07" + "22".repeat(32) + "00"
                        + "00"
        ), MinimapWireCodec.encode(withoutHint));

        RuntimeWireMessage.RequestMarkerReset withHint =
                new RuntimeWireMessage.RequestMarkerReset(
                        withoutHint.requestId(),
                        withoutHint.lease(),
                        withoutHint.runtime(),
                        Optional.of(new WireIdentity.MarkerStreamCursor(
                                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                                10
                        ))
                );
        byte[] encoded = MinimapWireCodec.encode(withHint);
        assertArrayEquals(hex(
                "0100048101"
                        + "00000000000000000000000000000004"
                        + "000809"
                        + targetHex()
                        + "086670736d6174636808746573745f6d6170"
                        + "07" + "22".repeat(32) + "00"
                        + "01"
                        + "00000000000000000000000000000005"
                        + "0a"
        ), encoded);
        org.junit.jupiter.api.Assertions.assertEquals(
                withHint,
                MinimapWireCodec.decode(
                        com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection.C2S,
                        encoded
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireIdentity.MarkerStreamCursor(UUID.randomUUID(), -1));
    }

    @Test
    void scopeAckMatchesTypedGolden() {
        RuntimeWireMessage.ScopeAck ack = new RuntimeWireMessage.ScopeAck(
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 11, 12),
                runtimeIdentity()
        );

        byte[] encoded = MinimapWireCodec.encode(ack);
        assertArrayEquals(hex(
                "0100416f"
                        + "00000000000000000000000000000006"
                        + "010b0c"
                        + targetHex()
                        + "086670736d6174636808746573745f6d6170"
                        + "07" + "22".repeat(32) + "00"
        ), encoded);
        org.junit.jupiter.api.Assertions.assertEquals(
                ack,
                MinimapWireCodec.decode(
                        com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection.S2C,
                        encoded
                )
        );
    }

    @Test
    void manifestMatchesTypedGoldenAndBindsObjectHashToRuntime() {
        byte[] data = new byte[]{1, 2, 3};
        Sha256 dataHash = new Sha256(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
        );
        WireIdentity.RuntimeIdentity runtime = runtimeIdentity(dataHash);
        WireTransfer.TransferFragment transfer = new WireTransfer.TransferFragment(
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                0,
                1,
                data.length,
                dataHash,
                dataHash,
                data
        );
        RuntimeWireMessage.Manifest manifest = new RuntimeWireMessage.Manifest(
                Optional.empty(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 13, 14),
                runtime,
                transfer
        );

        byte[] encoded = MinimapWireCodec.encode(manifest);
        assertArrayEquals(hex(
                "010042b701"
                        + "00"
                        + "000d0e"
                        + targetHex()
                        + "086670736d6174636808746573745f6d6170"
                        + "07" + dataHash.value() + "00"
                        + "00000000000000000000000000000007"
                        + "000103"
                        + dataHash.value() + dataHash.value()
                        + "03010203"
        ), encoded);
        org.junit.jupiter.api.Assertions.assertEquals(
                manifest,
                MinimapWireCodec.decode(
                        com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection.S2C,
                        encoded
                )
        );

        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.Manifest(
                Optional.empty(), manifest.lease(), runtime,
                new WireTransfer.TransferFragment(
                        transfer.transferId(), 0, 1, data.length,
                        new Sha256("55".repeat(32)), dataHash, data
                )
        ));
    }

    @Test
    void entryFragmentMatchesTypedGolden() {
        byte[] data = new byte[]{1, 2, 3};
        Sha256 dataHash = new Sha256(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
        );
        WireTransfer.TransferFragment transfer = new WireTransfer.TransferFragment(
                UUID.fromString("00000000-0000-0000-0000-000000000008"),
                0, 1, data.length, dataHash, dataHash, data
        );
        RuntimeWireMessage.EntryFragment entry = new RuntimeWireMessage.EntryFragment(
                UUID.fromString("00000000-0000-0000-0000-000000000009"),
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 15, 16),
                runtimeIdentity(),
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("a.json"),
                transfer
        );

        byte[] encoded = MinimapWireCodec.encode(entry);
        assertArrayEquals(hex(
                "010043cd01"
                        + "00000000000000000000000000000009"
                        + "010f10"
                        + targetHex()
                        + "086670736d6174636808746573745f6d6170"
                        + "07" + "22".repeat(32) + "00"
                        + "06612e6a736f6e"
                        + "00000000000000000000000000000008"
                        + "000103"
                        + dataHash.value() + dataHash.value()
                        + "03010203"
        ), encoded);
        org.junit.jupiter.api.Assertions.assertEquals(
                entry,
                MinimapWireCodec.decode(
                        com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection.S2C,
                        encoded
                )
        );
    }

    @Test
    void maximumBusinessFragmentFitsInsideIndependentFrameBudget() {
        byte[] data = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        Sha256 dataHash = com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest.of(data);
        RuntimeWireMessage.Manifest manifest = new RuntimeWireMessage.Manifest(
                Optional.of(UUID.randomUUID()),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtimeIdentity(dataHash),
                new WireTransfer.TransferFragment(
                        UUID.randomUUID(), 0, 1, data.length,
                        dataHash, dataHash, data
                )
        );

        byte[] frame = MinimapWireCodec.encode(manifest);

        assertTrue(frame.length > MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES);
        assertTrue(frame.length <= MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        assertTrue(frame.length - data.length
                <= MinimapHardLimits.MAX_WIRE_FRAGMENT_METADATA_BYTES);
        assertEquals(manifest, MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame));
    }

    @Test
    void typedRuntimeBodiesRejectTrailingBytesWrongDirectionAndInvalidScope() {
        RuntimeWireMessage.ScopeAck ack = new RuntimeWireMessage.ScopeAck(
                UUID.randomUUID(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtimeIdentity()
        );
        byte[] frame = MinimapWireCodec.encode(ack);

        assertWireError(MinimapErrorCode.WRONG_DIRECTION,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame));

        byte[] trailing = java.util.Arrays.copyOf(frame, frame.length + 1);
        int bodyLengthOffset = 3;
        assertTrue((trailing[bodyLengthOffset] & 0x80) == 0);
        trailing[bodyLengthOffset]++;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.S2C, trailing));

        byte[] invalidScope = frame.clone();
        int bodyOffset = 4;
        int scopeOffset = bodyOffset + 16;
        invalidScope[scopeOffset] = 2;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.S2C, invalidScope));
    }

    @Test
    void requestEntriesDefensivelyCopiesItsList() {
        WireTransfer.EntryRequest first = new WireTransfer.EntryRequest(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("a.json"),
                new Sha256("44".repeat(32))
        );
        java.util.ArrayList<WireTransfer.EntryRequest> source = new java.util.ArrayList<>();
        source.add(first);
        RuntimeWireMessage.RequestEntries request = new RuntimeWireMessage.RequestEntries(
                UUID.randomUUID(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtimeIdentity(), source
        );

        source.clear();
        assertEquals(List.of(first), request.entries());
        assertThrows(UnsupportedOperationException.class,
                () -> request.entries().add(first));
    }

    @Test
    void concreteRuntimeTransfersEnforceManifestAndEntryLengthLimits() {
        byte[] full = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        Sha256 fragmentHash = com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest.of(full);
        long oversizedManifest = MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES + 1;
        WireTransfer.TransferFragment manifestTransfer = new WireTransfer.TransferFragment(
                UUID.randomUUID(),
                0,
                Math.toIntExact((oversizedManifest - 1)
                        / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1),
                oversizedManifest,
                runtimeIdentity().runtimeHash(),
                fragmentHash,
                full
        );
        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.Manifest(
                Optional.empty(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtimeIdentity(),
                manifestTransfer
        ));

        long oversizedEntry = MinimapHardLimits.MAX_ZIP_ENTRY_BYTES + 1;
        WireTransfer.TransferFragment entryTransfer = new WireTransfer.TransferFragment(
                UUID.randomUUID(),
                0,
                Math.toIntExact((oversizedEntry - 1)
                        / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1),
                oversizedEntry,
                new Sha256("66".repeat(32)),
                fragmentHash,
                full
        );
        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.EntryFragment(
                UUID.randomUUID(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtimeIdentity(),
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("a.bin"),
                entryTransfer
        ));
    }

    @Test
    void manifestTotalLimitIsRejectedBeforeReadingFragmentHashesOrData() {
        WireWriter body = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        body.writeBoolean(false);
        body.writeUnsignedByte(WireIdentity.Scope.MATCH_HUD.code());
        body.writeNonNegativeVarLong(0);
        body.writeNonNegativeVarLong(0);
        body.writeUtf8("a", MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES);
        body.writeUtf8("M", MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES);
        body.writeUtf8("a", MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES);
        body.writeUtf8("d", MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES);
        body.writeUtf8("a", MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES);
        body.writeUtf8("o", MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES);
        body.writeNonNegativeVarLong(0);
        body.writeHash(new Sha256("11".repeat(32)));
        body.writeBoolean(false);
        body.writeUuid(UUID.randomUUID());
        body.writeUnsignedVarInt(0);
        long oversized = MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES + 1;
        body.writeUnsignedVarInt(Math.toIntExact(
                (oversized - 1) / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1
        ));
        body.writeNonNegativeVarLong(oversized);

        byte[] encodedBody = body.toByteArray();
        assertTrue(encodedBody.length < 128);
        byte[] frame = new byte[4 + encodedBody.length];
        frame[0] = 1;
        frame[1] = 0;
        frame[2] = 0x42;
        frame[3] = (byte) encodedBody.length;
        System.arraycopy(encodedBody, 0, frame, 4, encodedBody.length);

        assertWireError(
                MinimapErrorCode.QUOTA_EXCEEDED,
                () -> MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );
    }

    @Test
    void runtimeIdentityValuesRejectNegativeCountersAndEditorScope() {
        assertThrows(IllegalArgumentException.class, () -> new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, -1, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 0, -1
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireIdentity.RuntimeHint(
                NamespacedId.parse("fpsmatch:test"), -1, new Sha256("00".repeat(32))
        ));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeWireMessage.Subscribe(
                UUID.randomUUID(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 0, 0),
                new WireIdentity.MapTarget(
                        new MapKey("fpsmatch:test", "Map A"),
                        NamespacedId.parse("minecraft:overworld")
                ),
                Optional.empty()
        ));
    }

    @Test
    void transferFragmentRejectsNonCanonicalShapeAndMismatchedHash() {
        UUID transferId = UUID.randomUUID();
        Sha256 zeroHash = new Sha256("00".repeat(32));
        byte[] full = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];

        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 1, 1, 1, zeroHash, new byte[]{0}
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 1, 0, zeroHash, new byte[]{0}
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 4096, MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES + 1,
                zeroHash, full
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 0, 1, zeroHash, new byte[]{0}
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 4097, MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES,
                zeroHash, full
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 2, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L,
                zeroHash, new byte[]{0}
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 1, 1, zeroHash, new byte[0]
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 1, 2, MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L,
                zeroHash, new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1]
        ));
        assertThrows(IllegalArgumentException.class, () -> fragment(
                transferId, 0, 1, 1, zeroHash, new byte[]{1}
        ));
    }

    @Test
    void maximumTransferUsesOverflowSafeCanonicalCount() {
        byte[] last = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        Sha256 fragmentHash = com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest.of(last);

        WireTransfer.TransferFragment fragment = fragment(
                UUID.randomUUID(),
                4095,
                4096,
                MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES,
                fragmentHash,
                last
        );

        assertArrayEquals(last, fragment.fragmentData());
        last[0] = 1;
        assertArrayEquals(new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES],
                fragment.fragmentData());
    }

    private static WireTransfer.TransferFragment fragment(
            UUID transferId,
            int fragmentIndex,
            int fragmentCount,
            long totalLength,
            Sha256 fragmentHash,
            byte[] data
    ) {
        return new WireTransfer.TransferFragment(
                transferId,
                fragmentIndex,
                fragmentCount,
                totalLength,
                new Sha256("22".repeat(32)),
                fragmentHash,
                data
        );
    }

    private static WireIdentity.RuntimeIdentity runtimeIdentity() {
        return runtimeIdentity(new Sha256("22".repeat(32)));
    }

    private static WireIdentity.RuntimeIdentity runtimeIdentity(Sha256 runtimeHash) {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("fpsmatch:test", "Map A"),
                                NamespacedId.parse("minecraft:overworld")
                        ),
                        NamespacedId.parse("fpsmatch:test_map")
                ),
                7,
                runtimeHash,
                Optional.empty()
        );
    }

    private static String targetHex() {
        return "0d6670736d617463683a74657374"
                + "054d61702041"
                + "096d696e656372616674096f766572776f726c64";
    }

    private static byte[] hex(String value) {
        return HEX.parseHex(value);
    }

    private static void assertWireError(MinimapErrorCode code, Runnable action) {
        MinimapWireError error = assertThrows(MinimapWireError.class, action::run);
        assertEquals(code, error.code());
    }
}
