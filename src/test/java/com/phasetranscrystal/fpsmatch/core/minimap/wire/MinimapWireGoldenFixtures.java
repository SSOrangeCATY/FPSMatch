package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Typed samples whose bytes are independently fixed in minimap-bodies.hex. */
public final class MinimapWireGoldenFixtures {
    private MinimapWireGoldenFixtures() {
    }

    public static Map<MinimapOpcode, MinimapWireMessage> messages() {
        EnumMap<MinimapOpcode, MinimapWireMessage> messages =
                new EnumMap<>(MinimapOpcode.class);

        add(messages, new RuntimeWireMessage.Subscribe(
                uuid(1),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 1, 2),
                runtimeTarget(),
                Optional.of(new WireIdentity.RuntimeHint(
                        NamespacedId.parse("fpsmatch:test_map"),
                        3,
                        hash("11")
                ))
        ));
        add(messages, new RuntimeWireMessage.Unsubscribe(
                uuid(2),
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 3, 4),
                runtimeTarget()
        ));
        add(messages, new RuntimeWireMessage.RequestEntries(
                uuid(3),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 5, 6),
                runtimeIdentity(),
                List.of(new WireTransfer.EntryRequest(ContainerPath.parse("a.json"), hash("44")))
        ));
        add(messages, new RuntimeWireMessage.RequestMarkerReset(
                uuid(4),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 8, 9),
                runtimeIdentity(),
                Optional.empty()
        ));
        add(messages, new RuntimeWireMessage.ScopeAck(
                uuid(6),
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 11, 12),
                runtimeIdentity()
        ));

        Sha256 dataHash = dataHash();
        add(messages, new RuntimeWireMessage.Manifest(
                Optional.empty(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 13, 14),
                runtimeIdentity(dataHash),
                transfer(7, dataHash)
        ));
        add(messages, new RuntimeWireMessage.EntryFragment(
                uuid(9),
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 15, 16),
                runtimeIdentity(),
                ContainerPath.parse("a.json"),
                transfer(8, dataHash)
        ));

        add(messages, markerReset());
        add(messages, markerDelta());

        add(messages, new EditorWireMessage.EditorOpen(
                uuid(1), editorLease(), binding().target(), binding().documentId(),
                WireEditor.OpenMode.OPEN_EXISTING, 3, Optional.of(hash("22"))
        ));
        add(messages, new EditorWireMessage.EditorResume(
                uuid(2),
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 3, 4),
                binding(), uuid(3), hash("33"), 5
        ));
        add(messages, new EditorWireMessage.RequestSourceEntries(
                uuid(3), editorContext(), hash("33"),
                List.of(new WireTransfer.EntryRequest(ContainerPath.parse("p"), hash("44")))
        ));
        add(messages, new EditorWireMessage.EditorOperation(
                uuid(4), editorContext(), 5, hash("22"), hash("33"),
                List.of(
                        new WireEditor.Put(
                                ContainerPath.parse("p"), WireEditor.MediaType.PNG,
                                Optional.of(hash("44")), hash("55"), uuid(15)
                        ),
                        new WireEditor.Delete(ContainerPath.parse("q"), hash("66"))
                )
        ));
        add(messages, new EditorWireMessage.UploadFragment(
                uuid(5), editorContext(),
                new WireEditor.UploadBegin(
                        WireEditor.UploadPurpose.SOURCE_ENTRY,
                        Optional.of(ContainerPath.parse("p")),
                        3, 1, dataHash
                )
        ));
        add(messages, new EditorWireMessage.SaveDraft(
                uuid(6), editorContext(), 4, hash("22"), true
        ));
        add(messages, new EditorWireMessage.EditorClose(
                uuid(7), editorContext(), WireEditor.CloseMode.DISCARD_DRAFT
        ));
        add(messages, new EditorWireMessage.EditorSession(
                uuid(8), editorContext(), 6, WireEditor.SourceAvailability.FLATTEN_ONLY
        ));
        add(messages, new EditorWireMessage.SourceManifest(
                uuid(9), editorContext(), hash("33"), dataHash, transfer(14, dataHash)
        ));
        add(messages, new EditorWireMessage.SourceFragment(
                uuid(10), editorContext(), hash("33"), ContainerPath.parse("p"),
                WireEditor.MediaType.PNG, transfer(14, dataHash)
        ));
        add(messages, new EditorWireMessage.EditorAck(
                uuid(11), editorContext(), new WireEditor.OperationAck()
        ));

        add(messages, new SnapshotWireMessage.RequestWorldSnapshot(
                uuid(16), editorContext(), uuid(14), hash("33"),
                new WireSnapshot.SectionKey(-1, 0, 1),
                new WireSnapshot.SectionKey(2, 3, 4),
                true,
                List.of(new WireSnapshot.RequestedChannel(NamespacedId.parse("a:c"), 5))
        ));
        add(messages, new SnapshotWireMessage.RequestDirtySections(
                uuid(17), editorContext(), 5, 6, 7
        ));
        add(messages, new SnapshotWireMessage.WorldSnapshotManifest(
                uuid(18), editorContext(), 5, 6, hash("33"), 0, 1,
                List.of(new WireSnapshot.SectionDescriptor(
                        new WireSnapshot.SectionKey(-1, 0, 1), 7, true,
                        List.of(new WireSnapshot.ChannelDescriptor(
                                NamespacedId.parse("a:c"), 5, 3, hash("44"), 1
                        ))
                ))
        ));
        add(messages, new SnapshotWireMessage.WorldSnapshotFragment(
                uuid(19), editorContext(), 5,
                new WireSnapshot.SectionKey(-1, 0, 1), 7,
                NamespacedId.parse("a:c"), 5, transfer(14, dataHash)
        ));
        add(messages, new SnapshotWireMessage.DirtySections(
                uuid(20), editorContext(), 5, 6, 7, true,
                List.of(new WireSnapshot.DirtySection(
                        new WireSnapshot.SectionKey(-1, 0, 1), 8
                ))
        ));

        add(messages, new PublishWireMessage.EditorRebase(
                uuid(21), editorContext(), new WireEditor.RebaseStart(5, hash("33"))
        ));
        add(messages, new PublishWireMessage.ReservePublish(uuid(22), editorContext()));
        add(messages, new PublishWireMessage.CommitPublish(
                uuid(23), editorContext(), "t", 5, uuid(26), uuid(27),
                hash("33"), hash("44"), hash("55")
        ));
        add(messages, new PublishWireMessage.QueryPublishStatus(
                uuid(24), editorLease(), binding(), "t", 5
        ));
        add(messages, new PublishWireMessage.EditorRebaseResult(
                uuid(21), editorLease(), binding(), uuid(25),
                WireStatus.RebaseResultStatus.MERGED,
                Optional.of(editorContext()), 5, hash("33"), 0, 1, List.of()
        ));
        add(messages, new PublishWireMessage.PublishReservation(
                uuid(40), editorContext(), "t", 5, 6, 7
        ));

        WireStatus.HashTriple hashes = new WireStatus.HashTriple(
                hash("33"), hash("44"), hash("55")
        );
        add(messages, new PublishWireMessage.PublishResult(
                uuid(23), editorLease(), binding(), "t", 5,
                WireStatus.PublishOutcome.COMMITTED,
                Optional.of(hashes), Optional.empty()
        ));
        add(messages, new PublishWireMessage.PublishStatus(
                uuid(24), editorLease(), binding(), "t", 5,
                WireStatus.PublishState.COMMITTED,
                Optional.of(3L), Optional.of(hashes), Optional.empty()
        ));
        add(messages, new PublishWireMessage.ErrorMessage(
                Optional.of(uuid(21)), Optional.of(editorLease()), Optional.of(binding()),
                Optional.of(0xfe),
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.INTERNAL_ERROR.code(),
                        WireStatus.RetryDisposition.QUERY_PUBLISH_STATUS,
                        "e"
                )
        ));

        return Map.copyOf(messages);
    }

    public static MinimapWireMessage message(MinimapOpcode opcode) {
        MinimapWireMessage message = messages().get(opcode);
        if (message == null) {
            throw new IllegalArgumentException("No golden message for " + opcode);
        }
        return message;
    }

    private static void add(
            EnumMap<MinimapOpcode, MinimapWireMessage> messages,
            MinimapWireMessage message
    ) {
        if (messages.put(message.opcode(), message) != null) {
            throw new IllegalStateException("Duplicate golden opcode " + message.opcode());
        }
    }

    private static MarkerWireMessage.Reset markerReset() {
        return new MarkerWireMessage.Reset(
                Optional.empty(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 1, 2),
                markerRuntimeIdentity(), uuid(1), 0, uuid(2), 0, 1,
                List.of(new WireMarker.Marker(
                        NamespacedId.parse("a:m"), NamespacedId.parse("a:t"),
                        NamespacedId.parse("a:s"),
                        1.0, -2.0, 0.5, 90.0f, 4,
                        Optional.of(5L), Optional.of("f"),
                        List.of(
                                field("a:a", new WireMarker.BoolValue(true)),
                                field("a:b", new WireMarker.SignedLongValue(-1)),
                                field("a:c", new WireMarker.UnsignedLongValue(6)),
                                field("a:d", new WireMarker.DoubleValue(1.5)),
                                field("a:e", new WireMarker.StringValue("x")),
                                field("a:f", new WireMarker.IdValue(NamespacedId.parse("b:i"))),
                                field("a:g", new WireMarker.UuidValue(uuid(3))),
                                field("a:h", new WireMarker.HashValue(hash("22"))),
                                field("a:i", new WireMarker.BytesValue(new byte[]{0x7f}))
                        )
                ))
        );
    }

    private static MarkerWireMessage.Delta markerDelta() {
        return new MarkerWireMessage.Delta(
                new WireIdentity.ScopeLease(WireIdentity.Scope.TACTICAL_SCREEN, 6, 7),
                markerRuntimeIdentity(), uuid(1), 8,
                List.of(
                        new WireMarker.Add(simpleMarker("a:a", 0.0, 0)),
                        new WireMarker.Update(simpleMarker("a:a", 1.0, 1)),
                        new WireMarker.Remove(NamespacedId.parse("a:b"))
                )
        );
    }

    private static WireMarker.Marker simpleMarker(String id, double x, long updatedTick) {
        return new WireMarker.Marker(
                NamespacedId.parse(id), NamespacedId.parse("a:t"),
                NamespacedId.parse("a:s"),
                x, 0.0, 0.0, 0.0f, updatedTick,
                Optional.empty(), Optional.empty(), List.of()
        );
    }

    private static WireMarker.StateField field(String key, WireMarker.StateValue value) {
        return new WireMarker.StateField(NamespacedId.parse(key), value);
    }

    private static WireIdentity.MapTarget runtimeTarget() {
        return new WireIdentity.MapTarget(
                new MapKey("fpsmatch:test", "Map A"),
                NamespacedId.parse("minecraft:overworld")
        );
    }

    private static WireIdentity.RuntimeIdentity runtimeIdentity() {
        return runtimeIdentity(hash("22"));
    }

    private static WireIdentity.RuntimeIdentity runtimeIdentity(Sha256 runtimeHash) {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        runtimeTarget(), NamespacedId.parse("fpsmatch:test_map")
                ),
                7,
                runtimeHash,
                Optional.empty()
        );
    }

    private static WireIdentity.RuntimeIdentity markerRuntimeIdentity() {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                new MapKey("g", "m"), NamespacedId.parse("a:d")
                        ),
                        NamespacedId.parse("a:o")
                ),
                3,
                hash("11"),
                Optional.empty()
        );
    }

    private static WireIdentity.ScopeLease editorLease() {
        return new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2);
    }

    private static WireIdentity.DocumentBinding binding() {
        return new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(
                        new MapKey("g", "m"), NamespacedId.parse("a:d")
                ),
                NamespacedId.parse("a:o")
        );
    }

    private static WireIdentity.EditorContext editorContext() {
        return new WireIdentity.EditorContext(
                editorLease(), binding(), uuid(12), uuid(13), 3,
                hash("11"), hash("22"), 4
        );
    }

    private static WireTransfer.TransferFragment transfer(long id, Sha256 hash) {
        return new WireTransfer.TransferFragment(
                uuid(id), 0, 1, 3, hash, hash, new byte[]{1, 2, 3}
        );
    }

    private static Sha256 dataHash() {
        return new Sha256(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
        );
    }

    private static Sha256 hash(String byteHex) {
        return new Sha256(byteHex.repeat(32));
    }

    private static UUID uuid(long lowBits) {
        return new UUID(0, lowBits);
    }
}
