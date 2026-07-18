package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapProtocolContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublishWireMessageTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void editorRebasePhasesMatchTypedGoldens() {
        PublishWireMessage.EditorRebase start = new PublishWireMessage.EditorRebase(
                uuid(21),
                editorContext(),
                new WireEditor.RebaseStart(5, new Sha256("33".repeat(32)))
        );
        PublishWireMessage.EditorRebase resolve = new PublishWireMessage.EditorRebase(
                uuid(21),
                editorContext(),
                new WireEditor.RebaseResolve(
                        uuid(25),
                        List.of(new WireEditor.Resolution(
                                new Sha256("44".repeat(32)),
                                WireEditor.ResolutionChoice.THEIRS
                        ))
                )
        );

        byte[] startFrame = MinimapWireCodec.encode(start);
        byte[] resolveFrame = MinimapWireCodec.encode(resolve);

        assertEquals(168, startFrame.length);
        assertArrayEquals(HEX.parseHex(
                "010016a301"
                        + uuidHex(21)
                        + editorContextHex()
                        + "0005"
                        + "33".repeat(32)
        ), startFrame);
        assertEquals(
                start,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, startFrame)
        );
        assertEquals(185, resolveFrame.length);
        assertArrayEquals(HEX.parseHex(
                "010016b401"
                        + uuidHex(21)
                        + editorContextHex()
                        + "01"
                        + uuidHex(25)
                        + "01"
                        + "44".repeat(32)
                        + "01"
        ), resolveFrame);
        assertEquals(
                resolve,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, resolveFrame)
        );
    }

    @Test
    void publishCommandsMatchTypedGoldens() {
        PublishWireMessage.ReservePublish reserve =
                new PublishWireMessage.ReservePublish(uuid(22), editorContext());
        PublishWireMessage.CommitPublish commit =
                new PublishWireMessage.CommitPublish(
                        uuid(23),
                        editorContext(),
                        "t",
                        5,
                        uuid(26),
                        uuid(27),
                        new Sha256("33".repeat(32)),
                        new Sha256("44".repeat(32)),
                        new Sha256("55".repeat(32))
                );
        PublishWireMessage.QueryPublishStatus query =
                new PublishWireMessage.QueryPublishStatus(
                        uuid(24),
                        new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                        binding(),
                        "t",
                        5
                );

        byte[] reserveFrame = MinimapWireCodec.encode(reserve);
        byte[] commitFrame = MinimapWireCodec.encode(commit);
        byte[] queryFrame = MinimapWireCodec.encode(query);

        assertEquals(134, reserveFrame.length);
        assertArrayEquals(HEX.parseHex(
                "0100198101" + uuidHex(22) + editorContextHex()
        ), reserveFrame);
        assertEquals(
                reserve,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, reserveFrame)
        );

        assertEquals(265, commitFrame.length);
        assertArrayEquals(HEX.parseHex(
                "01001a8402"
                        + uuidHex(23)
                        + editorContextHex()
                        + "017405"
                        + uuidHex(26)
                        + uuidHex(27)
                        + "33".repeat(32)
                        + "44".repeat(32)
                        + "55".repeat(32)
        ), commitFrame);
        assertEquals(
                commit,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, commitFrame)
        );

        assertEquals(38, queryFrame.length);
        assertArrayEquals(HEX.parseHex(
                "01001c22"
                        + uuidHex(24)
                        + "020102"
                        + "0167016d016101640161016f"
                        + "017405"
        ), queryFrame);
        assertEquals(
                query,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, queryFrame)
        );
    }

    @Test
    void publishReservationMatchesTypedGolden() {
        PublishWireMessage.PublishReservation reservation =
                new PublishWireMessage.PublishReservation(
                        uuid(40), editorContext(), "t", 5, 6, 7
                );

        byte[] frame = MinimapWireCodec.encode(reservation);

        assertEquals(139, frame.length);
        assertArrayEquals(HEX.parseHex(
                "0100588601"
                        + uuidHex(40)
                        + editorContextHex()
                        + "0174050607"
        ), frame);
        assertEquals(
                reservation,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );
    }

    @Test
    void editorRebaseResultVariantsMatchTypedGoldens() {
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        PublishWireMessage.EditorRebaseResult merged =
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21),
                        lease,
                        binding(),
                        uuid(25),
                        WireStatus.RebaseResultStatus.MERGED,
                        Optional.of(editorContext()),
                        5,
                        new Sha256("33".repeat(32)),
                        0,
                        1,
                        List.of()
                );
        PublishWireMessage.EditorRebaseResult conflicts =
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21),
                        lease,
                        binding(),
                        uuid(25),
                        WireStatus.RebaseResultStatus.CONFLICTS,
                        Optional.empty(),
                        5,
                        new Sha256("33".repeat(32)),
                        0,
                        1,
                        List.of(new WireEditor.Conflict(
                                new Sha256("44".repeat(32)),
                                new WireEditor.PathSubject(ContainerPath.parse("p")),
                                Optional.of(new Sha256("55".repeat(32))),
                                Optional.of(new Sha256("66".repeat(32)))
                        ))
                );

        byte[] mergedFrame = MinimapWireCodec.encode(merged);
        byte[] conflictsFrame = MinimapWireCodec.encode(conflicts);

        assertEquals(203, mergedFrame.length);
        assertArrayEquals(HEX.parseHex(
                "010054c601"
                        + uuidHex(21)
                        + "020102"
                        + "0167016d016101640161016f"
                        + uuidHex(25)
                        + "0001"
                        + editorContextHex()
                        + "05"
                        + "33".repeat(32)
                        + "000100"
        ), mergedFrame);
        assertEquals(
                merged,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, mergedFrame)
        );

        assertEquals(191, conflictsFrame.length);
        assertArrayEquals(HEX.parseHex(
                "010054ba01"
                        + uuidHex(21)
                        + "020102"
                        + "0167016d016101640161016f"
                        + uuidHex(25)
                        + "0100"
                        + "05"
                        + "33".repeat(32)
                        + "000101"
                        + "44".repeat(32)
                        + "00"
                        + "0170"
                        + "01"
                        + "55".repeat(32)
                        + "01"
                        + "66".repeat(32)
        ), conflictsFrame);
        assertEquals(
                conflicts,
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, conflictsFrame)
        );
    }

    @Test
    void publishResultOutcomesMatchTypedGoldens() {
        WireStatus.HashTriple hashes = new WireStatus.HashTriple(
                new Sha256("33".repeat(32)),
                new Sha256("44".repeat(32)),
                new Sha256("55".repeat(32))
        );
        WireStatus.ErrorInfo error = new WireStatus.ErrorInfo(
                MinimapErrorCode.INTERNAL_ERROR.code(),
                WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS,
                "e"
        );
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        List<PublishWireMessage.PublishResult> messages = List.of(
                new PublishWireMessage.PublishResult(
                        uuid(23), lease, binding(), "t", 5,
                        WireStatus.PublishOutcome.COMMITTED,
                        Optional.of(hashes), Optional.empty()
                ),
                new PublishWireMessage.PublishResult(
                        uuid(23), lease, binding(), "t", 5,
                        WireStatus.PublishOutcome.ABORTED,
                        Optional.empty(), Optional.of(error)
                ),
                new PublishWireMessage.PublishResult(
                        uuid(23), lease, binding(), "t", 5,
                        WireStatus.PublishOutcome.STATUS_UNKNOWN,
                        Optional.empty(), Optional.of(error)
                )
        );
        List<String> goldens = List.of(
                "0100598501"
                        + uuidHex(23)
                        + "020102"
                        + "0167016d016101640161016f"
                        + "0174050001"
                        + "33".repeat(32)
                        + "44".repeat(32)
                        + "55".repeat(32)
                        + "00",
                "0100592a"
                        + uuidHex(23)
                        + "020102"
                        + "0167016d016101640161016f"
                        + "0174050100017fff040165",
                "0100592a"
                        + uuidHex(23)
                        + "020102"
                        + "0167016d016101640161016f"
                        + "0174050200017fff040165"
        );
        List<Integer> lengths = List.of(138, 46, 46);

        for (int index = 0; index < messages.size(); index++) {
            byte[] frame = MinimapWireCodec.encode(messages.get(index));
            assertEquals(lengths.get(index), frame.length);
            assertArrayEquals(HEX.parseHex(goldens.get(index)), frame);
            assertEquals(
                    messages.get(index),
                    MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
            );
        }
    }

    @Test
    void publishStatusStatesMatchTypedGoldens() {
        WireStatus.HashTriple hashes = new WireStatus.HashTriple(
                new Sha256("33".repeat(32)),
                new Sha256("44".repeat(32)),
                new Sha256("55".repeat(32))
        );
        WireStatus.ErrorInfo error = new WireStatus.ErrorInfo(
                MinimapErrorCode.INTERNAL_ERROR.code(),
                WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS,
                "e"
        );
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        List<PublishWireMessage.PublishStatus> messages = List.of(
                new PublishWireMessage.PublishStatus(
                        uuid(24), lease, binding(), "t", 5,
                        WireStatus.PublishState.RESERVED,
                        Optional.of(3L), Optional.empty(), Optional.empty()
                ),
                new PublishWireMessage.PublishStatus(
                        uuid(24), lease, binding(), "t", 5,
                        WireStatus.PublishState.PREPARED,
                        Optional.of(3L), Optional.empty(), Optional.empty()
                ),
                new PublishWireMessage.PublishStatus(
                        uuid(24), lease, binding(), "t", 5,
                        WireStatus.PublishState.COMMITTED,
                        Optional.of(3L), Optional.of(hashes), Optional.empty()
                ),
                new PublishWireMessage.PublishStatus(
                        uuid(24), lease, binding(), "t", 5,
                        WireStatus.PublishState.ABORTED,
                        Optional.of(3L), Optional.empty(), Optional.of(error)
                ),
                new PublishWireMessage.PublishStatus(
                        uuid(24), lease, binding(), "t", 5,
                        WireStatus.PublishState.STATUS_UNKNOWN,
                        Optional.empty(), Optional.empty(), Optional.of(error)
                )
        );
        String prefix = uuidHex(24)
                + "020102"
                + "0167016d016101640161016f"
                + "017405";
        List<String> goldens = List.of(
                "01005a27" + prefix + "0001030000",
                "01005a27" + prefix + "0101030000",
                "01005a8701" + prefix + "02010301"
                        + "33".repeat(32)
                        + "44".repeat(32)
                        + "55".repeat(32)
                        + "00",
                "01005a2c" + prefix + "03010300017fff040165",
                "01005a2b" + prefix + "040000017fff040165"
        );
        List<Integer> lengths = List.of(43, 43, 140, 48, 47);

        for (int index = 0; index < messages.size(); index++) {
            byte[] frame = MinimapWireCodec.encode(messages.get(index));
            assertEquals(lengths.get(index), frame.length);
            assertArrayEquals(HEX.parseHex(goldens.get(index)), frame);
            assertEquals(
                    messages.get(index),
                    MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
            );
        }
    }

    @Test
    void errorMessageMatchesTypedGoldenAndKeepsRawFailedOpcode() {
        PublishWireMessage.ErrorMessage message = new PublishWireMessage.ErrorMessage(
                Optional.of(uuid(21)),
                Optional.of(new WireIdentity.ScopeLease(
                        WireIdentity.Scope.EDITOR, 1, 2
                )),
                Optional.of(binding()),
                Optional.of(0xfe),
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.INTERNAL_ERROR.code(),
                        WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS,
                        "e"
                )
        );

        byte[] frame = MinimapWireCodec.encode(message);

        assertEquals(45, frame.length);
        assertArrayEquals(HEX.parseHex(
                "01007f29"
                        + "01"
                        + uuidHex(21)
                        + "01020102"
                        + "01"
                        + "0167016d016101640161016f"
                        + "01fe"
                        + "7fff040165"
        ), frame);
        PublishWireMessage.ErrorMessage decoded =
                (PublishWireMessage.ErrorMessage) MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C, frame
                );
        assertEquals(message, decoded);
        assertEquals(Optional.of(0xfe), decoded.failedOpcode());
    }

    @Test
    void taskSixTagsAndRebaseLimitsAreStable() {
        assertEquals(
                List.of(0, 1),
                Arrays.stream(WireEditor.ResolutionChoice.values())
                        .map(WireEditor.ResolutionChoice::code).toList()
        );
        assertEquals(
                List.of(0, 1),
                Arrays.stream(WireStatus.RebaseResultStatus.values())
                        .map(WireStatus.RebaseResultStatus::code).toList()
        );
        assertEquals(
                List.of(0, 1, 2),
                Arrays.stream(WireStatus.PublishOutcome.values())
                        .map(WireStatus.PublishOutcome::code).toList()
        );
        assertEquals(
                List.of(0, 1, 2, 3, 4),
                Arrays.stream(WireStatus.PublishState.values())
                        .map(WireStatus.PublishState::code).toList()
        );
        assertEquals(
                List.of(0, 1, 2, 3, 4),
                Arrays.stream(WireStatus.RetryDisposition.values())
                        .map(WireStatus.RetryDisposition::code).toList()
        );
        assertEquals(128, MinimapHardLimits.MAX_REBASE_ITEMS);
        assertEquals(0, new WireEditor.PathSubject(ContainerPath.parse("p")).tag());
        assertEquals(1, new WireEditor.IdSubject(NamespacedId.parse("a:c")).tag());

        WireEditor.Resolution resolution = new WireEditor.Resolution(
                new Sha256("44".repeat(32)), WireEditor.ResolutionChoice.OURS
        );
        WireEditor.RebaseResolve maximum = new WireEditor.RebaseResolve(
                uuid(25), Collections.nCopies(128, resolution)
        );
        PublishWireMessage.EditorRebase maximumMessage =
                new PublishWireMessage.EditorRebase(
                        uuid(21), editorContext(), maximum
                );
        byte[] maximumFrame = MinimapWireCodec.encode(maximumMessage);
        assertEquals(0x80, Byte.toUnsignedInt(maximumFrame[151]));
        assertEquals(0x01, Byte.toUnsignedInt(maximumFrame[152]));
        assertEquals(
                maximumMessage,
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, maximumFrame)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireEditor.RebaseResolve(
                        uuid(25), Collections.nCopies(129, resolution)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireEditor.RebaseStart(-1, new Sha256("33".repeat(32)))
        );

        byte[] hostileCount = maximumFrame.clone();
        hostileCount[151] = (byte) 0x81;
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, hostileCount)
        );

        PublishWireMessage.EditorRebase start = new PublishWireMessage.EditorRebase(
                uuid(21), editorContext(),
                new WireEditor.RebaseStart(0, new Sha256("33".repeat(32)))
        );
        byte[] unknownPhase = MinimapWireCodec.encode(start);
        assertEquals(0, unknownPhase[134]);
        unknownPhase[134] = 2;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, unknownPhase)
        );

        PublishWireMessage.EditorRebase oneResolution =
                new PublishWireMessage.EditorRebase(
                        uuid(21), editorContext(),
                        new WireEditor.RebaseResolve(uuid(25), List.of(resolution))
                );
        byte[] unknownChoice = MinimapWireCodec.encode(oneResolution);
        unknownChoice[unknownChoice.length - 1] = 2;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.C2S, unknownChoice)
        );
    }

    @Test
    void rebaseResultMatrixAndConflictSubjectsAreEnforced() {
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        Sha256 conflictHash = new Sha256("44".repeat(32));
        for (boolean ours : List.of(false, true)) {
            for (boolean theirs : List.of(false, true)) {
                WireEditor.Conflict conflict = new WireEditor.Conflict(
                        conflictHash,
                        ours
                                ? new WireEditor.IdSubject(NamespacedId.parse("a:c"))
                                : new WireEditor.PathSubject(ContainerPath.parse("p")),
                        ours ? Optional.of(new Sha256("55".repeat(32))) : Optional.empty(),
                        theirs ? Optional.of(new Sha256("66".repeat(32))) : Optional.empty()
                );
                PublishWireMessage.EditorRebaseResult result =
                        new PublishWireMessage.EditorRebaseResult(
                                uuid(21), lease, binding(), uuid(25),
                                WireStatus.RebaseResultStatus.CONFLICTS,
                                Optional.empty(), 0, new Sha256("33".repeat(32)),
                                0, 1, List.of(conflict)
                        );
                assertEquals(
                        result,
                        MinimapWireCodec.decode(
                                MinimapMessageDirection.S2C,
                                MinimapWireCodec.encode(result)
                        )
                );
            }
        }

        WireEditor.Conflict conflict = new WireEditor.Conflict(
                conflictHash,
                new WireEditor.PathSubject(ContainerPath.parse("p")),
                Optional.empty(), Optional.empty()
        );
        PublishWireMessage.EditorRebaseResult maximum =
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21), lease, binding(), uuid(25),
                        WireStatus.RebaseResultStatus.CONFLICTS,
                        Optional.empty(), Long.MAX_VALUE,
                        new Sha256("33".repeat(32)),
                        4_095, 4_096, Collections.nCopies(128, conflict)
                );
        assertEquals(
                maximum,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        MinimapWireCodec.encode(maximum)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21), lease, binding(), uuid(25),
                        WireStatus.RebaseResultStatus.MERGED,
                        Optional.empty(), 0, new Sha256("33".repeat(32)),
                        0, 1, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21), lease, binding(), uuid(25),
                        WireStatus.RebaseResultStatus.MERGED,
                        Optional.of(editorContext()), 0,
                        new Sha256("33".repeat(32)),
                        0, 1, List.of(conflict)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21), lease, binding(), uuid(25),
                        WireStatus.RebaseResultStatus.CONFLICTS,
                        Optional.empty(), 0, new Sha256("33".repeat(32)),
                        0, 1, List.of()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21), lease, binding(), uuid(25),
                        WireStatus.RebaseResultStatus.CONFLICTS,
                        Optional.empty(), 0, new Sha256("33".repeat(32)),
                        0, 1, Collections.nCopies(129, conflict)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21),
                        new WireIdentity.ScopeLease(
                                WireIdentity.Scope.MATCH_HUD, 1, 2
                        ),
                        binding(), uuid(25),
                        WireStatus.RebaseResultStatus.CONFLICTS,
                        Optional.empty(), 0, new Sha256("33".repeat(32)),
                        0, 1, List.of(conflict)
                )
        );
    }

    @Test
    void publishResultPresenceMatrixHasOneShapePerOutcome() {
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        WireStatus.HashTriple hashes = hashes();
        WireStatus.ErrorInfo error = errorInfo();
        for (WireStatus.PublishOutcome outcome : WireStatus.PublishOutcome.values()) {
            int accepted = 0;
            for (boolean hashPresent : List.of(false, true)) {
                for (boolean errorPresent : List.of(false, true)) {
                    boolean expected = outcome == WireStatus.PublishOutcome.COMMITTED
                            ? hashPresent && !errorPresent
                            : !hashPresent && errorPresent;
                    boolean actual;
                    try {
                        new PublishWireMessage.PublishResult(
                                uuid(23), lease, binding(), "t", 0, outcome,
                                hashPresent ? Optional.of(hashes) : Optional.empty(),
                                errorPresent ? Optional.of(error) : Optional.empty()
                        );
                        actual = true;
                        accepted++;
                    } catch (IllegalArgumentException invalid) {
                        actual = false;
                    }
                    assertEquals(
                            expected, actual,
                            outcome + " hash=" + hashPresent + " error=" + errorPresent
                    );
                }
            }
            assertEquals(1, accepted, outcome.name());
        }
    }

    @Test
    void publishStatusPresenceMatrixHasOneShapePerState() {
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        for (WireStatus.PublishState state : WireStatus.PublishState.values()) {
            int accepted = 0;
            for (boolean currentPresent : List.of(false, true)) {
                for (boolean hashPresent : List.of(false, true)) {
                    for (boolean errorPresent : List.of(false, true)) {
                        boolean expected = switch (state) {
                            case RESERVED, PREPARED -> currentPresent
                                    && !hashPresent && !errorPresent;
                            case COMMITTED -> currentPresent
                                    && hashPresent && !errorPresent;
                            case ABORTED -> currentPresent
                                    && !hashPresent && errorPresent;
                            case STATUS_UNKNOWN -> !currentPresent
                                    && !hashPresent && errorPresent;
                        };
                        boolean actual;
                        try {
                            new PublishWireMessage.PublishStatus(
                                    uuid(24), lease, binding(), "t", 0, state,
                                    currentPresent ? Optional.of(0L) : Optional.empty(),
                                    hashPresent ? Optional.of(hashes()) : Optional.empty(),
                                    errorPresent ? Optional.of(errorInfo()) : Optional.empty()
                            );
                            actual = true;
                            accepted++;
                        } catch (IllegalArgumentException invalid) {
                            actual = false;
                        }
                        assertEquals(
                                expected, actual,
                                state + " current=" + currentPresent
                                        + " hash=" + hashPresent
                                        + " error=" + errorPresent
                        );
                    }
                }
            }
            assertEquals(1, accepted, state.name());
        }
    }

    @Test
    void publishTokenErrorDetailAndErrorOptionalsAreBounded() {
        assertEquals(128, MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES);
        assertEquals(1_024, MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES);
        String token = "t".repeat(MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES);
        PublishWireMessage.CommitPublish maximumToken =
                new PublishWireMessage.CommitPublish(
                        uuid(23), editorContext(), token, Long.MAX_VALUE,
                        uuid(26), uuid(27),
                        new Sha256("33".repeat(32)),
                        new Sha256("44".repeat(32)),
                        new Sha256("55".repeat(32))
                );
        assertEquals(
                maximumToken,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        MinimapWireCodec.encode(maximumToken)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.CommitPublish(
                        uuid(23), editorContext(), token + "x", 0,
                        uuid(26), uuid(27),
                        new Sha256("33".repeat(32)),
                        new Sha256("44".repeat(32)),
                        new Sha256("55".repeat(32))
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.CommitPublish(
                        uuid(23), editorContext(), "bad\uD800", 0,
                        uuid(26), uuid(27),
                        new Sha256("33".repeat(32)),
                        new Sha256("44".repeat(32)),
                        new Sha256("55".repeat(32))
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.QueryPublishStatus(
                        uuid(24),
                        new WireIdentity.ScopeLease(
                                WireIdentity.Scope.MATCH_HUD, 0, 0
                        ),
                        binding(), "t", 0
                )
        );

        WireStatus.ErrorInfo maximumDetail = new WireStatus.ErrorInfo(
                MinimapErrorCode.INTERNAL_ERROR.code(),
                WireStatus.RetryDisposition.DO_NOT_RETRY,
                "e".repeat(MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES)
        );
        assertEquals(MinimapErrorCode.INTERNAL_ERROR, maximumDetail.knownCode());
        assertThrows(IllegalArgumentException.class, () ->
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.INTERNAL_ERROR.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        maximumDetail.detail() + "e"
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireStatus.ErrorInfo(
                        0x0005,
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "e"
                )
        );

        for (int mask = 0; mask < 16; mask++) {
            PublishWireMessage.ErrorMessage message =
                    new PublishWireMessage.ErrorMessage(
                            (mask & 1) != 0 ? Optional.of(uuid(21)) : Optional.empty(),
                            (mask & 2) != 0
                                    ? Optional.of(new WireIdentity.ScopeLease(
                                    WireIdentity.Scope.MATCH_HUD, 0, 0
                            )) : Optional.empty(),
                            (mask & 4) != 0 ? Optional.of(binding()) : Optional.empty(),
                            (mask & 8) != 0 ? Optional.of(0xff) : Optional.empty(),
                            errorInfo()
                    );
            assertEquals(
                    message,
                    MinimapWireCodec.decode(
                            MinimapMessageDirection.S2C,
                            MinimapWireCodec.encode(message)
                    ),
                    "optional mask " + mask
            );
        }
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.ErrorMessage(
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(-1), errorInfo()
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.ErrorMessage(
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(256), errorInfo()
                )
        );
    }

    @Test
    void hostilePublishFramesRejectUnknownTagsPresenceAndErrorCodes() {
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR, 1, 2
        );
        PublishWireMessage.PublishResult committed =
                new PublishWireMessage.PublishResult(
                        uuid(23), lease, binding(), "t", 5,
                        WireStatus.PublishOutcome.COMMITTED,
                        Optional.of(hashes()), Optional.empty()
                );
        byte[] unknownOutcome = MinimapWireCodec.encode(committed);
        assertEquals(0, unknownOutcome[39]);
        unknownOutcome[39] = 3;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, unknownOutcome)
        );
        byte[] missingHashes = MinimapWireCodec.encode(committed);
        assertEquals(1, missingHashes[40]);
        missingHashes[40] = 0;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, missingHashes)
        );
        byte[] unexpectedError = MinimapWireCodec.encode(committed);
        unexpectedError[unexpectedError.length - 1] = 1;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, unexpectedError)
        );

        PublishWireMessage.PublishStatus reserved =
                new PublishWireMessage.PublishStatus(
                        uuid(24), lease, binding(), "t", 5,
                        WireStatus.PublishState.RESERVED,
                        Optional.of(3L), Optional.empty(), Optional.empty()
                );
        byte[] unknownState = MinimapWireCodec.encode(reserved);
        assertEquals(0, unknownState[38]);
        unknownState[38] = 5;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, unknownState)
        );
        byte[] missingCurrent = MinimapWireCodec.encode(reserved);
        assertEquals(1, missingCurrent[39]);
        missingCurrent[39] = 0;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, missingCurrent)
        );

        PublishWireMessage.EditorRebaseResult conflictResult =
                new PublishWireMessage.EditorRebaseResult(
                        uuid(21), lease, binding(), uuid(25),
                        WireStatus.RebaseResultStatus.CONFLICTS,
                        Optional.empty(), 5, new Sha256("33".repeat(32)),
                        0, 1,
                        List.of(new WireEditor.Conflict(
                                new Sha256("44".repeat(32)),
                                new WireEditor.PathSubject(ContainerPath.parse("p")),
                                Optional.empty(), Optional.empty()
                        ))
                );
        byte[] unknownRebaseStatus = MinimapWireCodec.encode(conflictResult);
        assertEquals(1, unknownRebaseStatus[51]);
        unknownRebaseStatus[51] = 2;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C, unknownRebaseStatus
                )
        );
        byte[] unknownSubject = MinimapWireCodec.encode(conflictResult);
        assertEquals(0, unknownSubject[121]);
        unknownSubject[121] = 2;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, unknownSubject)
        );

        PublishWireMessage.ErrorMessage errorMessage =
                new PublishWireMessage.ErrorMessage(
                        Optional.of(uuid(21)), Optional.of(lease),
                        Optional.of(binding()), Optional.of(0xfe), errorInfo()
                );
        byte[] unknownErrorCode = MinimapWireCodec.encode(errorMessage);
        int errorStart = unknownErrorCode.length - 5;
        unknownErrorCode[errorStart] = 0;
        unknownErrorCode[errorStart + 1] = 5;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, unknownErrorCode)
        );
        byte[] unknownRetry = MinimapWireCodec.encode(errorMessage);
        unknownRetry[unknownRetry.length - 3] = 5;
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, unknownRetry)
        );

        byte[] invalidMergedBeforeOversizedCount = HEX.parseHex(
                "01005456"
                        + uuidHex(21)
                        + "020102"
                        + "0167016d016101640161016f"
                        + uuidHex(25)
                        + "0000"
                        + "05"
                        + "33".repeat(32)
                        + "0001"
                        + "8101"
        );
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        invalidMergedBeforeOversizedCount
                )
        );

        byte[] wrongScopeBeforeOversizedCount = HEX.parseHex(
                "01005456"
                        + uuidHex(21)
                        + "000102"
                        + "0167016d016101640161016f"
                        + uuidHex(25)
                        + "0100"
                        + "05"
                        + "33".repeat(32)
                        + "0001"
                        + "8101"
        );
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        wrongScopeBeforeOversizedCount
                )
        );
    }

    @Test
    void taskSixDirectionsAndTrailingBytesAreRejected() {
        PublishWireMessage.ReservePublish reserve =
                new PublishWireMessage.ReservePublish(uuid(22), editorContext());
        byte[] frame = MinimapWireCodec.encode(reserve);
        assertWireError(MinimapErrorCode.WRONG_DIRECTION, () ->
                MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
        );
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        Arrays.copyOf(frame, frame.length + 1)
                )
        );
    }

    @Test
    void publishResultAndStatusHostileWireMatricesMatchConstructors() {
        for (WireStatus.PublishOutcome outcome : WireStatus.PublishOutcome.values()) {
            for (int mask = 0; mask < 4; mask++) {
                boolean hashPresent = (mask & 1) != 0;
                boolean errorPresent = (mask & 2) != 0;
                WireWriter body = publishPrefix(uuid(23));
                body.writeUnsignedByte(outcome.code());
                body.writeBoolean(hashPresent);
                if (hashPresent) {
                    writeHashes(body);
                }
                body.writeBoolean(errorPresent);
                if (errorPresent) {
                    writeError(body);
                }
                byte[] frame = wrapFrame(MinimapOpcode.S2C_PUBLISH_RESULT, body);
                boolean valid = outcome == WireStatus.PublishOutcome.COMMITTED
                        ? hashPresent && !errorPresent
                        : !hashPresent && errorPresent;
                if (valid) {
                    assertEquals(
                            MinimapOpcode.S2C_PUBLISH_RESULT,
                            assertDoesNotThrow(() -> MinimapWireCodec.decode(
                                    MinimapMessageDirection.S2C, frame
                            )).opcode()
                    );
                } else {
                    assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                            MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
                    );
                }
            }
        }

        for (WireStatus.PublishState state : WireStatus.PublishState.values()) {
            for (int mask = 0; mask < 8; mask++) {
                boolean currentPresent = (mask & 1) != 0;
                boolean hashPresent = (mask & 2) != 0;
                boolean errorPresent = (mask & 4) != 0;
                WireWriter body = publishPrefix(uuid(24));
                body.writeUnsignedByte(state.code());
                body.writeBoolean(currentPresent);
                if (currentPresent) {
                    body.writeNonNegativeVarLong(3);
                }
                body.writeBoolean(hashPresent);
                if (hashPresent) {
                    writeHashes(body);
                }
                body.writeBoolean(errorPresent);
                if (errorPresent) {
                    writeError(body);
                }
                byte[] frame = wrapFrame(MinimapOpcode.S2C_PUBLISH_STATUS, body);
                boolean valid = switch (state) {
                    case RESERVED, PREPARED -> currentPresent
                            && !hashPresent && !errorPresent;
                    case COMMITTED -> currentPresent
                            && hashPresent && !errorPresent;
                    case ABORTED -> currentPresent
                            && !hashPresent && errorPresent;
                    case STATUS_UNKNOWN -> !currentPresent
                            && !hashPresent && errorPresent;
                };
                if (valid) {
                    assertEquals(
                            MinimapOpcode.S2C_PUBLISH_STATUS,
                            assertDoesNotThrow(() -> MinimapWireCodec.decode(
                                    MinimapMessageDirection.S2C, frame
                            )).opcode()
                    );
                } else {
                    assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                            MinimapWireCodec.decode(MinimapMessageDirection.S2C, frame)
                    );
                }
            }
        }
    }

    @Test
    void rebaseResultHostileWireMatrixValidatesBeforeConflictAllocation() {
        for (WireStatus.RebaseResultStatus status
                : WireStatus.RebaseResultStatus.values()) {
            for (boolean contextPresent : List.of(false, true)) {
                for (boolean validPage : List.of(false, true)) {
                    for (int conflictCount : List.of(0, 1, 129)) {
                        WireWriter body = new WireWriter(
                                MinimapHardLimits.MAX_WIRE_FRAME_BYTES
                        );
                        body.writeUuid(uuid(21));
                        WireValueCodec.writeLease(
                                body,
                                new WireIdentity.ScopeLease(
                                        WireIdentity.Scope.EDITOR, 1, 2
                                )
                        );
                        WireValueCodec.writeDocumentBinding(body, binding());
                        body.writeUuid(uuid(25));
                        body.writeUnsignedByte(status.code());
                        body.writeBoolean(contextPresent);
                        if (contextPresent) {
                            WireValueCodec.writeEditorContext(body, editorContext());
                        }
                        body.writeNonNegativeVarLong(5);
                        body.writeHash(new Sha256("33".repeat(32)));
                        body.writeUnsignedVarInt(validPage ? 0 : 1);
                        body.writeUnsignedVarInt(1);
                        body.writeUnsignedVarInt(conflictCount);
                        if (conflictCount == 1) {
                            writeConflict(body);
                        }
                        byte[] frame = wrapFrame(
                                MinimapOpcode.S2C_EDITOR_REBASE_RESULT, body
                        );
                        boolean validHeader = status
                                == WireStatus.RebaseResultStatus.MERGED
                                ? contextPresent && validPage
                                : !contextPresent && validPage;
                        boolean validCount = status
                                == WireStatus.RebaseResultStatus.MERGED
                                ? conflictCount == 0
                                : conflictCount == 1;
                        if (!validHeader) {
                            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                                    MinimapWireCodec.decode(
                                            MinimapMessageDirection.S2C, frame
                                    )
                            );
                        } else if (conflictCount == 129) {
                            assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                                    MinimapWireCodec.decode(
                                            MinimapMessageDirection.S2C, frame
                                    )
                            );
                        } else if (validCount) {
                            assertEquals(
                                    MinimapOpcode.S2C_EDITOR_REBASE_RESULT,
                                    assertDoesNotThrow(() -> MinimapWireCodec.decode(
                                            MinimapMessageDirection.S2C, frame
                                    )).opcode()
                            );
                        } else {
                            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                                    MinimapWireCodec.decode(
                                            MinimapMessageDirection.S2C, frame
                                    )
                            );
                        }
                    }
                }
            }
        }
    }

    @Test
    void hostileTokenAndDetailLengthsFailBeforePayloadBytes() {
        String exactToken = "\u00e9".repeat(64);
        PublishWireMessage.QueryPublishStatus exact =
                new PublishWireMessage.QueryPublishStatus(
                        uuid(24),
                        new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                        binding(), exactToken, 0
                );
        assertEquals(
                exact,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        MinimapWireCodec.encode(exact)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PublishWireMessage.QueryPublishStatus(
                        uuid(24),
                        new WireIdentity.ScopeLease(
                                WireIdentity.Scope.EDITOR, 1, 2
                        ),
                        binding(), "\u00e9".repeat(65), 0
                )
        );

        WireWriter tokenBody = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        tokenBody.writeUuid(uuid(24));
        WireValueCodec.writeLease(
                tokenBody,
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2)
        );
        WireValueCodec.writeDocumentBinding(tokenBody, binding());
        tokenBody.writeUnsignedVarInt(129);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        wrapFrame(MinimapOpcode.C2S_EDITOR_QUERY_PUBLISH_STATUS, tokenBody)
                )
        );

        WireStatus.ErrorInfo exactDetail = new WireStatus.ErrorInfo(
                MinimapErrorCode.INTERNAL_ERROR.code(),
                WireStatus.RetryDisposition.DO_NOT_RETRY,
                "\u00e9".repeat(512)
        );
        PublishWireMessage.ErrorMessage exactError =
                new PublishWireMessage.ErrorMessage(
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), exactDetail
                );
        assertEquals(
                exactError,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        MinimapWireCodec.encode(exactError)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.INTERNAL_ERROR.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "\u00e9".repeat(513)
                )
        );

        WireWriter detailBody = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        detailBody.writeBoolean(false);
        detailBody.writeBoolean(false);
        detailBody.writeBoolean(false);
        detailBody.writeBoolean(false);
        detailBody.writeUnsignedShort(MinimapErrorCode.INTERNAL_ERROR.code());
        detailBody.writeUnsignedByte(WireStatus.RetryDisposition.DO_NOT_RETRY.code());
        detailBody.writeUnsignedVarInt(1_025);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        wrapFrame(MinimapOpcode.S2C_ERROR, detailBody)
                )
        );

        WireWriter unknownBeforeOversizedDetail = new WireWriter(
                MinimapHardLimits.MAX_WIRE_FRAME_BYTES
        );
        unknownBeforeOversizedDetail.writeBoolean(false);
        unknownBeforeOversizedDetail.writeBoolean(false);
        unknownBeforeOversizedDetail.writeBoolean(false);
        unknownBeforeOversizedDetail.writeBoolean(false);
        unknownBeforeOversizedDetail.writeUnsignedShort(0x0005);
        unknownBeforeOversizedDetail.writeUnsignedByte(
                WireStatus.RetryDisposition.DO_NOT_RETRY.code()
        );
        unknownBeforeOversizedDetail.writeUnsignedVarInt(1_025);
        assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                MinimapWireCodec.decode(
                        MinimapMessageDirection.S2C,
                        wrapFrame(MinimapOpcode.S2C_ERROR, unknownBeforeOversizedDetail)
                )
        );
    }

    private static WireWriter publishPrefix(UUID requestId) {
        WireWriter writer = new WireWriter(MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        writer.writeUuid(requestId);
        WireValueCodec.writeLease(
                writer,
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2)
        );
        WireValueCodec.writeDocumentBinding(writer, binding());
        writer.writeUtf8("t", MinimapHardLimits.MAX_PUBLISH_TOKEN_UTF8_BYTES);
        writer.writeNonNegativeVarLong(5);
        return writer;
    }

    private static void writeHashes(WireWriter writer) {
        writer.writeHash(new Sha256("33".repeat(32)));
        writer.writeHash(new Sha256("44".repeat(32)));
        writer.writeHash(new Sha256("55".repeat(32)));
    }

    private static void writeError(WireWriter writer) {
        writer.writeUnsignedShort(MinimapErrorCode.INTERNAL_ERROR.code());
        writer.writeUnsignedByte(
                WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS.code()
        );
        writer.writeUtf8("e", MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES);
    }

    private static void writeConflict(WireWriter writer) {
        writer.writeHash(new Sha256("44".repeat(32)));
        writer.writeUnsignedByte(0);
        writer.writeUtf8("p", MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
    }

    private static byte[] wrapFrame(MinimapOpcode opcode, WireWriter bodyWriter) {
        byte[] body = bodyWriter.toByteArray();
        int lengthBytes = unsignedVarIntSize(body.length);
        byte[] frame = new byte[3 + lengthBytes + body.length];
        frame[0] = (byte) MinimapProtocolContract.WIRE_MAJOR;
        frame[1] = (byte) MinimapProtocolContract.WIRE_MINOR;
        frame[2] = (byte) opcode.code();
        int cursor = writeUnsignedVarInt(frame, 3, body.length);
        System.arraycopy(body, 0, frame, cursor, body.length);
        return frame;
    }

    private static int writeUnsignedVarInt(byte[] output, int cursor, int value) {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            output[cursor++] = (byte) ((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        output[cursor++] = (byte) remaining;
        return cursor;
    }

    private static int unsignedVarIntSize(int value) {
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static WireStatus.HashTriple hashes() {
        return new WireStatus.HashTriple(
                new Sha256("33".repeat(32)),
                new Sha256("44".repeat(32)),
                new Sha256("55".repeat(32))
        );
    }

    private static WireStatus.ErrorInfo errorInfo() {
        return new WireStatus.ErrorInfo(
                MinimapErrorCode.INTERNAL_ERROR.code(),
                WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS,
                "e"
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

    private static WireIdentity.DocumentBinding binding() {
        return new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(
                        new MapKey("g", "m"),
                        NamespacedId.parse("a:d")
                ),
                NamespacedId.parse("a:o")
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

    private static UUID uuid(long lowBits) {
        return new UUID(0, lowBits);
    }

    private static String uuidHex(long lowBits) {
        return "%032x".formatted(lowBits);
    }
}
