package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditorWireMessageTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void editorOpenMatchesTypedGoldenAndRequiresEditorScope() {
        EditorWireMessage.EditorOpen open = new EditorWireMessage.EditorOpen(
                uuid(1),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                new WireIdentity.MapTarget(
                        new MapKey("g", "m"),
                        NamespacedId.parse("a:d")
                ),
                NamespacedId.parse("a:o"),
                WireEditor.OpenMode.OPEN_EXISTING,
                3,
                Optional.of(new Sha256("22".repeat(32)))
        );

        byte[] frame = MinimapWireCodec.encode(open);

        assertEquals(70, frame.length);
        assertArrayEquals(HEX.parseHex(
                "01001042"
                        + "00000000000000000000000000000001"
                        + "020102"
                        + "0167016d01610164"
                        + "0161016f"
                        + "000301"
                        + "22".repeat(32)
        ), frame);
        assertEquals(
                open,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
        );

        assertThrows(IllegalArgumentException.class, () -> new EditorWireMessage.EditorOpen(
                open.requestId(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 1, 2),
                open.target(),
                open.documentId(),
                open.openMode(),
                open.expectedRevision(),
                open.expectedRuntimeHash()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EditorWireMessage.EditorOpen(
                open.requestId(),
                open.lease(),
                open.target(),
                open.documentId(),
                open.openMode(),
                -1,
                open.expectedRuntimeHash()
        ));
    }

    @Test
    void editorResumeMatchesTypedGoldenAndBoundsAckCursor() {
        EditorWireMessage.EditorResume resume = new EditorWireMessage.EditorResume(
                uuid(2),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 3, 4),
                binding(),
                uuid(3),
                new Sha256("33".repeat(32)),
                5
        );

        byte[] frame = MinimapWireCodec.encode(resume);

        assertEquals(84, frame.length);
        assertArrayEquals(HEX.parseHex(
                "01001150"
                        + "00000000000000000000000000000002"
                        + "020304"
                        + "0167016d016101640161016f"
                        + "00000000000000000000000000000003"
                        + "33".repeat(32)
                        + "05"
        ), frame);
        assertEquals(
                resume,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
        );
        assertThrows(IllegalArgumentException.class, () -> new EditorWireMessage.EditorResume(
                resume.requestId(),
                resume.lease(),
                resume.binding(),
                resume.draftId(),
                resume.draftRootHash(),
                -1
        ));
    }

    @Test
    void requestSourceEntriesMatchesTypedGoldenAndBoundsCount() {
        WireTransfer.EntryRequest entry = new WireTransfer.EntryRequest(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("p"),
                new Sha256("44".repeat(32))
        );
        EditorWireMessage.RequestSourceEntries request =
                new EditorWireMessage.RequestSourceEntries(
                        uuid(3),
                        editorContext(),
                        new Sha256("33".repeat(32)),
                        List.of(entry)
                );

        byte[] frame = MinimapWireCodec.encode(request);

        assertEquals(201, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010012c401"
                        + uuidHex(3)
                        + editorContextHex()
                        + "33".repeat(32)
                        + "01"
                        + "0170"
                        + "44".repeat(32)
        ), frame);
        assertEquals(
                request,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EditorWireMessage.RequestSourceEntries(
                        request.requestId(),
                        request.context(),
                        request.sourceHash(),
                        Collections.nCopies(257, entry)
                )
        );
    }

    @Test
    void operationRoundTripsAtomicPutDeleteMutations() {
        WireEditor.Put put = new WireEditor.Put(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("p"),
                WireEditor.MediaType.PNG,
                Optional.of(new Sha256("44".repeat(32))),
                new Sha256("55".repeat(32)),
                uuid(15)
        );
        WireEditor.Delete delete = new WireEditor.Delete(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("q"),
                new Sha256("66".repeat(32))
        );
        EditorWireMessage.EditorOperation operation =
                new EditorWireMessage.EditorOperation(
                        uuid(4),
                        editorContext(),
                        5,
                        new Sha256("22".repeat(32)),
                        new Sha256("33".repeat(32)),
                        List.of(put, delete)
                );

        byte[] frame = MinimapWireCodec.encode(operation);

        assertEquals(320, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010013bb02"
                        + uuidHex(4)
                        + editorContextHex()
                        + "05"
                        + "22".repeat(32)
                        + "33".repeat(32)
                        + "02"
                        + "00" + "0170" + "01" + "01"
                        + "44".repeat(32)
                        + "55".repeat(32)
                        + uuidHex(15)
                        + "01" + "0171"
                        + "66".repeat(32)
        ), frame);
        assertEquals(
                operation,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
        );
        assertEquals(64, MinimapHardLimits.MAX_EDITOR_MUTATIONS);
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.EditorOperation(
                        operation.requestId(), operation.context(), 0,
                        operation.expectedRootHash(), operation.payloadHash(), List.of()
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.EditorOperation(
                        operation.requestId(), operation.context(), 0,
                        operation.expectedRootHash(), operation.payloadHash(),
                        Collections.nCopies(65, put)
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.EditorOperation(
                        operation.requestId(), operation.context(), -1,
                        operation.expectedRootHash(), operation.payloadHash(), List.of(put)
        ));
    }

    @Test
    void operationDecodeAccepts64MutationsAndRejects65BeforeReadingThem() {
        WireEditor.Put put = new WireEditor.Put(
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("p"),
                WireEditor.MediaType.PNG,
                Optional.empty(),
                new Sha256("55".repeat(32)),
                uuid(15)
        );
        EditorWireMessage.EditorOperation maximum = new EditorWireMessage.EditorOperation(
                uuid(4), editorContext(), 0,
                new Sha256("22".repeat(32)),
                new Sha256("33".repeat(32)),
                Collections.nCopies(MinimapHardLimits.MAX_EDITOR_MUTATIONS, put)
        );

        byte[] maximumFrame = MinimapWireCodec.encode(maximum);

        assertEquals(
                maximum,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, maximumFrame)
        );

        EditorWireMessage.EditorOperation oneMutation = new EditorWireMessage.EditorOperation(
                uuid(4), editorContext(), 0,
                new Sha256("22".repeat(32)),
                new Sha256("33".repeat(32)),
                List.of(put)
        );
        byte[] hostile = MinimapWireCodec.encode(oneMutation);
        assertEquals(1, hostile[199]);
        hostile[199] = 65;

        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, hostile)
        );
    }

    @Test
    void editorEnumTagsRemainStable() {
        assertEquals(
                List.of(0, 1, 2, 3),
                Arrays.stream(WireEditor.OpenMode.values())
                        .map(WireEditor.OpenMode::code)
                        .toList()
        );
        assertEquals(
                List.of(0, 1, 2),
                Arrays.stream(WireEditor.MediaType.values())
                        .map(WireEditor.MediaType::code)
                        .toList()
        );
        assertEquals(
                List.of(0, 1, 2),
                Arrays.stream(WireEditor.UploadPurpose.values())
                        .map(WireEditor.UploadPurpose::code)
                        .toList()
        );
        assertEquals(
                List.of(0, 1),
                Arrays.stream(WireEditor.CloseMode.values())
                        .map(WireEditor.CloseMode::code)
                        .toList()
        );
        assertEquals(
                List.of(0, 1, 2),
                Arrays.stream(WireEditor.SourceAvailability.values())
                        .map(WireEditor.SourceAvailability::code)
                        .toList()
        );
    }

    @Test
    void uploadActionsMatchGoldensAndBeginGeometryIsCanonical() {
        Sha256 dataHash = new Sha256(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
        );
        WireEditor.UploadBegin beginData = new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.SOURCE_ENTRY,
                Optional.of(com.phasetranscrystal.fpsmatch.core.minimap.model
                        .ContainerPath.parse("p")),
                3,
                1,
                dataHash
        );
        EditorWireMessage.UploadFragment begin = new EditorWireMessage.UploadFragment(
                uuid(5), editorContext(), beginData
        );
        WireTransfer.TransferFragment transfer = new WireTransfer.TransferFragment(
                uuid(14),
                0,
                1,
                3,
                dataHash,
                dataHash,
                new byte[]{1, 2, 3}
        );
        List<EditorWireMessage.UploadFragment> messages = List.of(
                begin,
                new EditorWireMessage.UploadFragment(
                        uuid(5), editorContext(), new WireEditor.UploadData(transfer)
                ),
                new EditorWireMessage.UploadFragment(
                        uuid(5), editorContext(), new WireEditor.UploadFinish(uuid(14))
                ),
                new EditorWireMessage.UploadFragment(
                        uuid(5), editorContext(), new WireEditor.UploadAbort(uuid(14))
                )
        );
        List<String> goldens = List.of(
                "010014a801" + uuidHex(5) + editorContextHex()
                        + "00020101700301" + dataHash.value(),
                "010014d901" + uuidHex(5) + editorContextHex()
                        + "01" + transferHex(dataHash),
                "0100149201" + uuidHex(5) + editorContextHex()
                        + "02" + uuidHex(14),
                "0100149201" + uuidHex(5) + editorContextHex()
                        + "03" + uuidHex(14)
        );

        for (int index = 0; index < messages.size(); index++) {
            byte[] frame = MinimapWireCodec.encode(messages.get(index));
            assertArrayEquals(HEX.parseHex(goldens.get(index)), frame);
            assertEquals(
                    messages.get(index),
                    MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame)
            );
        }

        assertEquals(1_073_741_824L, MinimapHardLimits.MAX_SOURCE_CONTAINER_UPLOAD_BYTES);
        assertEquals(572_915_734L, MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES);
        assertEquals(134_217_728L, MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES);
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.SOURCE_ENTRY,
                Optional.empty(),
                3,
                1,
                dataHash
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.SOURCE_CONTAINER,
                beginData.path(),
                3,
                1,
                dataHash
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.SOURCE_ENTRY,
                beginData.path(),
                0,
                1,
                dataHash
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.SOURCE_ENTRY,
                beginData.path(),
                3,
                2,
                dataHash
        ));
        long runtimeTooLarge = MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES + 1;
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.RUNTIME_CONTAINER,
                Optional.empty(),
                runtimeTooLarge,
                canonicalFragmentCount(runtimeTooLarge),
                dataHash
        ));
    }

    @Test
    void uploadBeginAcceptsEachPurposeMaximumAndRejectsTheNextByte() {
        Sha256 expectedHash = new Sha256("44".repeat(32));
        List<WireEditor.UploadBegin> maximums = List.of(
                new WireEditor.UploadBegin(
                        WireEditor.UploadPurpose.SOURCE_CONTAINER,
                        Optional.empty(),
                        MinimapHardLimits.MAX_SOURCE_CONTAINER_UPLOAD_BYTES,
                        canonicalFragmentCount(
                                MinimapHardLimits.MAX_SOURCE_CONTAINER_UPLOAD_BYTES
                        ),
                        expectedHash
                ),
                new WireEditor.UploadBegin(
                        WireEditor.UploadPurpose.RUNTIME_CONTAINER,
                        Optional.empty(),
                        MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES,
                        canonicalFragmentCount(
                                MinimapHardLimits.MAX_RUNTIME_CONTAINER_UPLOAD_BYTES
                        ),
                        expectedHash
                ),
                new WireEditor.UploadBegin(
                        WireEditor.UploadPurpose.SOURCE_ENTRY,
                        Optional.of(com.phasetranscrystal.fpsmatch.core.minimap.model
                                .ContainerPath.parse("p")),
                        MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES,
                        canonicalFragmentCount(
                                MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES
                        ),
                        expectedHash
                )
        );

        for (WireEditor.UploadBegin begin : maximums) {
            EditorWireMessage.UploadFragment message = new EditorWireMessage.UploadFragment(
                    uuid(5), editorContext(), begin
            );
            assertEquals(
                    message,
                    MinimapWireCodec.decode(
                            MinimapMessageDirection.C2S,
                            MinimapWireCodec.encode(message)
                    )
            );
            long tooLarge = begin.totalLength() + 1;
            assertThrows(IllegalArgumentException.class, () ->
                    new WireEditor.UploadBegin(
                            begin.purpose(), begin.path(), tooLarge,
                            canonicalFragmentCount(tooLarge), expectedHash
                    )
            );
        }
    }

    @Test
    void uploadDecodeRejectsUnknownPurposeAndMismatchedActionBody() {
        WireEditor.UploadBegin begin = new WireEditor.UploadBegin(
                WireEditor.UploadPurpose.SOURCE_ENTRY,
                Optional.of(com.phasetranscrystal.fpsmatch.core.minimap.model
                        .ContainerPath.parse("p")),
                3,
                1,
                dataHash()
        );
        byte[] unknownPurpose = MinimapWireCodec.encode(
                new EditorWireMessage.UploadFragment(uuid(5), editorContext(), begin)
        );
        assertEquals(0, unknownPurpose[134]);
        assertEquals(2, unknownPurpose[135]);
        unknownPurpose[135] = 3;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, unknownPurpose)
        );

        byte[] mismatchedAction = MinimapWireCodec.encode(
                new EditorWireMessage.UploadFragment(
                        uuid(5), editorContext(), new WireEditor.UploadFinish(uuid(14))
                )
        );
        assertEquals(2, mismatchedAction[134]);
        mismatchedAction[134] = 1;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, mismatchedAction)
        );
    }

    @Test
    void saveDraftMatchesTypedGolden() {
        EditorWireMessage.SaveDraft save = new EditorWireMessage.SaveDraft(
                uuid(6),
                editorContext(),
                4,
                new Sha256("22".repeat(32)),
                true
        );

        byte[] frame = MinimapWireCodec.encode(save);

        assertEquals(168, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010015a301"
                        + uuidHex(6)
                        + editorContextHex()
                        + "04"
                        + "22".repeat(32)
                        + "01"
        ), frame);
        assertEquals(save, MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame));
        assertThrows(IllegalArgumentException.class, () -> new EditorWireMessage.SaveDraft(
                save.requestId(), save.context(), -1,
                save.expectedRootHash(), save.compact()
        ));
    }

    @Test
    void editorCloseMatchesTypedGolden() {
        EditorWireMessage.EditorClose close = new EditorWireMessage.EditorClose(
                uuid(7), editorContext(), WireEditor.CloseMode.DISCARD_DRAFT
        );

        byte[] frame = MinimapWireCodec.encode(close);

        assertEquals(135, frame.length);
        assertArrayEquals(HEX.parseHex(
                "01001b8201"
                        + uuidHex(7)
                        + editorContextHex()
                        + "01"
        ), frame);
        assertEquals(close, MinimapWireCodec.decode(MinimapMessageDirection.C2S, frame));
    }

    @Test
    void editorSessionMatchesTypedGolden() {
        EditorWireMessage.EditorSession session = new EditorWireMessage.EditorSession(
                uuid(8),
                editorContext(),
                6,
                WireEditor.SourceAvailability.FLATTEN_ONLY
        );

        byte[] frame = MinimapWireCodec.encode(session);

        assertEquals(136, frame.length);
        assertArrayEquals(HEX.parseHex(
                "0100508301"
                        + uuidHex(8)
                        + editorContextHex()
                        + "0601"
        ), frame);
        assertEquals(session, MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame));
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.EditorSession(
                        session.requestId(), session.context(), -1,
                        session.sourceAvailability()
        ));
    }

    @Test
    void sourceManifestMatchesTypedGoldenAndBindsManifestHash() {
        Sha256 dataHash = dataHash();
        WireTransfer.TransferFragment transfer = smallTransfer(dataHash);
        EditorWireMessage.SourceManifest manifest = new EditorWireMessage.SourceManifest(
                uuid(9),
                editorContext(),
                new Sha256("33".repeat(32)),
                dataHash,
                transfer
        );

        byte[] frame = MinimapWireCodec.encode(manifest);

        assertEquals(285, frame.length);
        assertArrayEquals(HEX.parseHex(
                "0100519802"
                        + uuidHex(9)
                        + editorContextHex()
                        + "33".repeat(32)
                        + dataHash.value()
                        + transferHex(dataHash)
        ), frame);
        assertEquals(manifest, MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame));
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.SourceManifest(
                        manifest.requestId(), manifest.context(), manifest.sourceHash(),
                        new Sha256("44".repeat(32)), transfer
                ));
    }

    @Test
    void sourceFragmentMatchesTypedGoldenAndUsesMediaSpecificLimit() {
        Sha256 dataHash = dataHash();
        WireTransfer.TransferFragment transfer = smallTransfer(dataHash);
        EditorWireMessage.SourceFragment fragment = new EditorWireMessage.SourceFragment(
                uuid(10),
                editorContext(),
                new Sha256("33".repeat(32)),
                com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("p"),
                WireEditor.MediaType.PNG,
                transfer
        );

        byte[] frame = MinimapWireCodec.encode(fragment);

        assertEquals(256, frame.length);
        assertArrayEquals(HEX.parseHex(
                "010052fb01"
                        + uuidHex(10)
                        + editorContextHex()
                        + "33".repeat(32)
                        + "0170"
                        + "01"
                        + transferHex(dataHash)
        ), frame);
        assertEquals(fragment, MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame));

        byte[] firstFragment = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        Sha256 fragmentHash = com.phasetranscrystal.fpsmatch.core.minimap.format
                .Sha256Digest.of(firstFragment);
        long jsonTooLarge = MinimapHardLimits.MAX_JSON_ENTRY_BYTES + 1;
        WireTransfer.TransferFragment oversized = new WireTransfer.TransferFragment(
                uuid(14),
                0,
                canonicalFragmentCount(jsonTooLarge),
                jsonTooLarge,
                dataHash,
                fragmentHash,
                firstFragment
        );
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.SourceFragment(
                        fragment.requestId(), fragment.context(), fragment.sourceHash(),
                        fragment.path(), WireEditor.MediaType.JSON, oversized
        ));
    }

    @Test
    void sourceTransfersAcceptExactMediaLimitsAndRejectTheNextByte() {
        Sha256 objectHash = new Sha256("77".repeat(32));
        WireTransfer.TransferFragment manifestMaximum = firstFragment(
                MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES, objectHash
        );
        EditorWireMessage.SourceManifest manifest = new EditorWireMessage.SourceManifest(
                uuid(9), editorContext(), new Sha256("33".repeat(32)),
                objectHash, manifestMaximum
        );
        assertEquals(
                manifest,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        MinimapWireCodec.encode(manifest)
                )
        );
        WireTransfer.TransferFragment oversizedManifest = firstFragment(
                MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES + 1, objectHash
        );
        assertThrows(IllegalArgumentException.class, () ->
                new EditorWireMessage.SourceManifest(
                        uuid(9), editorContext(), new Sha256("33".repeat(32)),
                        objectHash, oversizedManifest
                )
        );

        List<WireEditor.MediaType> mediaTypes = List.of(
                WireEditor.MediaType.JSON,
                WireEditor.MediaType.PNG,
                WireEditor.MediaType.BINARY
        );
        for (WireEditor.MediaType mediaType : mediaTypes) {
            long maximum = mediaType == WireEditor.MediaType.JSON
                    ? MinimapHardLimits.MAX_JSON_ENTRY_BYTES
                    : MinimapHardLimits.MAX_ZIP_ENTRY_BYTES;
            EditorWireMessage.SourceFragment fragment = new EditorWireMessage.SourceFragment(
                    uuid(10), editorContext(), new Sha256("33".repeat(32)),
                    com.phasetranscrystal.fpsmatch.core.minimap.model
                            .ContainerPath.parse("p"),
                    mediaType,
                    firstFragment(maximum, objectHash)
            );
            assertEquals(
                    fragment,
                    MinimapWireCodec.decode(
                            MinimapMessageDirection.S2C,
                            MinimapWireCodec.encode(fragment)
                    )
            );
            WireTransfer.TransferFragment oversized = firstFragment(
                    maximum + 1, objectHash
            );
            assertThrows(IllegalArgumentException.class, () ->
                    new EditorWireMessage.SourceFragment(
                            fragment.requestId(), fragment.context(), fragment.sourceHash(),
                            fragment.path(), mediaType, oversized
                    )
            );
        }
    }

    @Test
    void editorAckVariantsMatchGoldensAndBindCompletionHash() {
        Sha256 dataHash = dataHash();
        List<EditorWireMessage.EditorAck> messages = List.of(
                new EditorWireMessage.EditorAck(
                        uuid(11), editorContext(), new WireEditor.OperationAck()
                ),
                new EditorWireMessage.EditorAck(
                        uuid(11), editorContext(), new WireEditor.DraftSaved(true)
                ),
                new EditorWireMessage.EditorAck(
                        uuid(11), editorContext(), new WireEditor.UploadAck(
                        uuid(14), 1, 3, false, Optional.empty()
                )),
                new EditorWireMessage.EditorAck(
                        uuid(11), editorContext(), new WireEditor.UploadAck(
                        uuid(14), 1, 3, true, Optional.of(dataHash)
                )),
                new EditorWireMessage.EditorAck(
                        uuid(11), editorContext(), new WireEditor.Closed(
                        WireEditor.CloseMode.DISCARD_DRAFT
                ))
        );
        List<String> goldens = List.of(
                "0100538201" + uuidHex(11) + editorContextHex() + "00",
                "0100538301" + uuidHex(11) + editorContextHex() + "0101",
                "0100539601" + uuidHex(11) + editorContextHex()
                        + "02" + uuidHex(14) + "01030000",
                "010053b601" + uuidHex(11) + editorContextHex()
                        + "02" + uuidHex(14) + "01030101" + dataHash.value(),
                "0100538301" + uuidHex(11) + editorContextHex() + "0301"
        );

        for (int index = 0; index < messages.size(); index++) {
            byte[] frame = MinimapWireCodec.encode(messages.get(index));
            assertArrayEquals(HEX.parseHex(goldens.get(index)), frame);
            assertEquals(
                    messages.get(index),
                    MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
            );
        }

        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadAck(
                uuid(14), 1, 3, true, Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadAck(
                uuid(14), 1, 3, false, Optional.of(dataHash)
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadAck(
                uuid(14), 4_097, 3, false, Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WireEditor.UploadAck(
                uuid(14), 1, MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES + 1,
                false, Optional.empty()
        ));
    }

    @Test
    void uploadAckDecodeRejectsCompletionHashPresenceMismatch() {
        EditorWireMessage.EditorAck incomplete = new EditorWireMessage.EditorAck(
                uuid(11), editorContext(), new WireEditor.UploadAck(
                        uuid(14), 1, 3, false, Optional.empty()
                )
        );
        byte[] completeWithoutHash = MinimapWireCodec.encode(incomplete);
        assertEquals(0, completeWithoutHash[completeWithoutHash.length - 2]);
        assertEquals(0, completeWithoutHash[completeWithoutHash.length - 1]);
        completeWithoutHash[completeWithoutHash.length - 2] = 1;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, completeWithoutHash)
        );

        byte[] hashWithoutBytes = MinimapWireCodec.encode(incomplete);
        hashWithoutBytes[hashWithoutBytes.length - 1] = 1;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, hashWithoutBytes)
        );

        EditorWireMessage.EditorAck complete = new EditorWireMessage.EditorAck(
                uuid(11), editorContext(), new WireEditor.UploadAck(
                        uuid(14), 1, 3, true, Optional.of(dataHash())
                )
        );
        byte[] incompleteWithHash = MinimapWireCodec.encode(complete);
        int hashPresence = incompleteWithHash.length - 32 - 1;
        assertEquals(1, incompleteWithHash[hashPresence]);
        incompleteWithHash[hashPresence] = 0;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, incompleteWithHash)
        );
    }

    @Test
    void unknownEditorTagsTrailingDirectionAndOpaqueCarriersAreRejected() {
        EditorWireMessage.EditorOpen open = new EditorWireMessage.EditorOpen(
                uuid(1),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                binding().target(),
                binding().documentId(),
                WireEditor.OpenMode.OPEN_EXISTING,
                0,
                Optional.empty()
        );
        byte[] openFrame = MinimapWireCodec.encode(open);
        byte[] unknownOpenMode = openFrame.clone();
        unknownOpenMode[35] = 4;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, unknownOpenMode
        ));
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, openFrame
        ));
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S,
                Arrays.copyOf(openFrame, openFrame.length + 1)
        ));

        EditorWireMessage.EditorOperation operation = new EditorWireMessage.EditorOperation(
                uuid(4), editorContext(), 0,
                new Sha256("22".repeat(32)),
                new Sha256("33".repeat(32)),
                List.of(new WireEditor.Put(
                        com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath.parse("p"),
                        WireEditor.MediaType.PNG,
                        Optional.empty(),
                        new Sha256("55".repeat(32)),
                        uuid(15)
                ))
        );
        byte[] operationFrame = MinimapWireCodec.encode(operation);
        byte[] emptyMutationList = operationFrame.clone();
        emptyMutationList[199] = 0;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, emptyMutationList
        ));
        byte[] unknownMutation = operationFrame.clone();
        unknownMutation[200] = 2;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, unknownMutation
        ));
        byte[] unknownMedia = operationFrame.clone();
        unknownMedia[203] = 3;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, unknownMedia
        ));

        EditorWireMessage.UploadFragment upload = new EditorWireMessage.UploadFragment(
                uuid(5), editorContext(), new WireEditor.UploadFinish(uuid(14))
        );
        byte[] unknownUploadAction = MinimapWireCodec.encode(upload);
        unknownUploadAction[134] = 4;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, unknownUploadAction
        ));

        EditorWireMessage.EditorClose close = new EditorWireMessage.EditorClose(
                uuid(7), editorContext(), WireEditor.CloseMode.KEEP_DRAFT
        );
        byte[] unknownCloseMode = MinimapWireCodec.encode(close);
        unknownCloseMode[134] = 2;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.C2S, unknownCloseMode
        ));

        EditorWireMessage.EditorSession session = new EditorWireMessage.EditorSession(
                uuid(8), editorContext(), 0, WireEditor.SourceAvailability.FULL_SOURCE
        );
        byte[] unknownAvailability = MinimapWireCodec.encode(session);
        unknownAvailability[135] = 3;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, unknownAvailability
        ));

        EditorWireMessage.SourceFragment sourceFragment =
                new EditorWireMessage.SourceFragment(
                        uuid(10), editorContext(), new Sha256("33".repeat(32)),
                        com.phasetranscrystal.fpsmatch.core.minimap.model
                                .ContainerPath.parse("p"),
                        WireEditor.MediaType.PNG,
                        smallTransfer(dataHash())
                );
        byte[] unknownSourceMedia = MinimapWireCodec.encode(sourceFragment);
        unknownSourceMedia[168] = 3;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, unknownSourceMedia
        ));

        EditorWireMessage.EditorAck ack = new EditorWireMessage.EditorAck(
                uuid(11), editorContext(), new WireEditor.OperationAck()
        );
        byte[] unknownAck = MinimapWireCodec.encode(ack);
        unknownAck[134] = 4;
        assertThrows(MinimapWireError.class, () -> MinimapWireCodec.decode(
                MinimapMessageDirection.S2C, unknownAck
        ));

        assertThrows(IllegalArgumentException.class, () -> new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                binding(), uuid(12), uuid(13), 0,
                new Sha256("11".repeat(32)), new Sha256("22".repeat(32)), 0
        ));
    }

    private static Sha256 dataHash() {
        return new Sha256(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
        );
    }

    private static WireTransfer.TransferFragment smallTransfer(Sha256 hash) {
        return new WireTransfer.TransferFragment(
                uuid(14), 0, 1, 3, hash, hash, new byte[]{1, 2, 3}
        );
    }

    private static WireTransfer.TransferFragment firstFragment(
            long totalLength,
            Sha256 objectHash
    ) {
        byte[] fragment = new byte[MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES];
        return new WireTransfer.TransferFragment(
                uuid(14),
                0,
                canonicalFragmentCount(totalLength),
                totalLength,
                objectHash,
                com.phasetranscrystal.fpsmatch.core.minimap.format
                        .Sha256Digest.of(fragment),
                fragment
        );
    }

    private static String transferHex(Sha256 hash) {
        return uuidHex(14)
                + "000103"
                + hash.value()
                + hash.value()
                + "03010203";
    }

    private static int canonicalFragmentCount(long totalLength) {
        return Math.toIntExact(
                (totalLength - 1) / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1
        );
    }

    private static void assertWireError(MinimapErrorCode expected, Runnable action) {
        MinimapWireError error = assertThrows(MinimapWireError.class, action::run);
        assertEquals(expected, error.code());
    }

    private static WireIdentity.EditorContext editorContext() {
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                binding(),
                uuid(12),
                uuid(13),
                3,
                new Sha256("11".repeat(32)),
                new Sha256("22".repeat(32)),
                4
        );
    }

    private static String editorContextHex() {
        return "020102"
                + "0167016d016101640161016f"
                + uuidHex(12)
                + uuidHex(13)
                + "03"
                + "11".repeat(32)
                + "22".repeat(32)
                + "04";
    }

    private static WireIdentity.DocumentBinding binding() {
        return new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(
                        new MapKey("g", "m"),
                        NamespacedId.parse("a:d")
                ),
                NamespacedId.parse("a:o")
        );
    }

    private static UUID uuid(long lowBits) {
        return new UUID(0, lowBits);
    }

    private static String uuidHex(long lowBits) {
        return "%032x".formatted(lowBits);
    }
}
