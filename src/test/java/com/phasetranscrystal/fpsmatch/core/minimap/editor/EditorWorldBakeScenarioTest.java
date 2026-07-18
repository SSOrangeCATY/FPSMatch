package com.phasetranscrystal.fpsmatch.core.minimap.editor;

import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.CloseChoice;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.CloseDecision;
import com.phasetranscrystal.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.phasetranscrystal.fpsmatch.common.minimap.server.WorldSectionKey;
import com.phasetranscrystal.fpsmatch.common.minimap.server.WorldSectionRevisionIndex;
import com.phasetranscrystal.fpsmatch.common.minimap.server.snapshot.SectionAccess;
import com.phasetranscrystal.fpsmatch.common.minimap.server.snapshot.ServerLevelSnapshotAdapter;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.BlockSampleRule;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.SamplerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.SectionCoord;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.SnapshotChannelId;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.WorldBakeRasterizer;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.WorldSectionSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.WorldSnapshotQuota;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.WorldSnapshotRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.WorldSnapshotService;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.UnloadedSectionPolicy;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.DraftSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommandLog;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorSessionGateway;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.RebaseResult;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.importing.ImagePlacementMode;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.importing.ImportedImageAsset;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.importing.PngImportService;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate 2 automated scenario covering source create -> snapshot -> bake -> paint ->
 * import path -> undo/redo -> reconnect -> draft save -> publish without a live Minecraft client.
 */
class EditorWorldBakeScenarioTest {
    @Test
    void fullEditorWorldBakeScenarioPassesWithoutLiveClient() {
        NamespacedId dimension = NamespacedId.parse("minecraft:overworld");
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();
        SectionCoord sectionCoord = new SectionCoord(0, 0, 0);
        index.markMutated(new WorldSectionKey(dimension, 0, 0, 0));

        SectionAccess access = (coord, channels) -> new SectionAccess.CopiedSection(
                List.of("minecraft:air", "minecraft:stone"),
                new byte[] {0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0},
                new short[16],
                new byte[16],
                new int[16]
        );
        ServerLevelSnapshotAdapter world = new ServerLevelSnapshotAdapter(
                dimension, index, access, () -> true
        );

        WorldSnapshotService snapshots = new WorldSnapshotService(
                new WorldSnapshotQuota(8, 65536, 1000),
                (actor, map) -> true
        );
        var manifest = snapshots.request(new WorldSnapshotRequest(
                UUID.randomUUID(),
                "admin",
                "map_a",
                dimension,
                List.of(sectionCoord),
                List.of(SnapshotChannelId.BLOCKS),
                UnloadedSectionPolicy.SKIP,
                0L
        ), world);
        assertEquals(1, manifest.sections().size());

        WorldSectionSnapshot section = world.copySection(
                sectionCoord, List.of(SnapshotChannelId.BLOCKS)
        ).orElseThrow();
        assertFalse(section.stale());

        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(4, 4), 4, "ground", DisplayLabel.literal("Ground")
        );
        SamplerProfile profile = new SamplerProfile(
                "default",
                1L,
                List.of(
                        BlockSampleRule.transparent("minecraft:air", 100),
                        BlockSampleRule.color("minecraft:stone", 0xFF808080, 50)
                )
        );
        String bakeLayer = new WorldBakeRasterizer(profile)
                .bakeIntoDocument(document, "ground", "gen", List.of(section));
        assertEquals(LayerType.WORLD_BAKE, document.layer("ground", bakeLayer).type());

        String paint = document.createLayer(
                "ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint")
        );
        int[] red = new int[16];
        java.util.Arrays.fill(red, 0xFFFF0000);
        document.putTilePixels("ground", paint, 0, 0, red);
        assertEquals(0xFFFF0000, document.tilePixels("ground", paint, 0, 0)[0]);

        // Imported PNG path must not require world alignment.
        byte[] rgba = new byte[4 * 4 * 4];
        for (int i = 0; i < 16; i++) {
            int o = i * 4;
            rgba[o] = (byte) 0x00;
            rgba[o + 1] = (byte) 0x80;
            rgba[o + 2] = (byte) 0xFF;
            rgba[o + 3] = (byte) 0xFF;
        }
        ImportedImageAsset imported = new PngImportService().importRgba(
                document, "ground", "overlay_png", 4, 4, rgba, ImagePlacementMode.ORIGINAL
        );
        assertTrue(document.floorIds().contains("ground"));
        assertEquals("overlay_png", imported.assetId());

        RecordingGateway gateway = new RecordingGateway();
        EditorCommandLog log = EditorCommandLog.empty(hash("scenario-base"));
        MinimapEditorController controller = MinimapEditorController.open(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                document,
                log,
                gateway,
                true
        );
        controller.selectLayer("ground", paint);
        controller.setLayerOpacity(0.5);
        assertTrue(controller.isDirty());
        controller.undo();
        controller.redo();
        controller.saveDraft();
        assertEquals(1, gateway.applyCount.get());
        controller.onReconnect();
        assertEquals(1, gateway.rebaseCount.get());
        controller.publish();
        assertEquals(1, gateway.publishCount.get());
        assertFalse(controller.isDirty());

        assertEquals(CloseDecision.CLOSED, controller.requestClose());
        assertTrue(controller.isClosed());
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingGateway implements EditorSessionGateway {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final AtomicInteger rebaseCount = new AtomicInteger();
        private final AtomicInteger publishCount = new AtomicInteger();
        private DraftSnapshot last;

        @Override
        public DraftSnapshot apply(UUID sessionId, UUID actorId, EditorCommand command, boolean authorized) {
            applyCount.incrementAndGet();
            last = new DraftSnapshot(
                    sessionId,
                    command.sequence(),
                    command.baseRootHash(),
                    command.resultingRootHash(),
                    List.of(command.operation())
            );
            return last;
        }

        @Override
        public DraftSnapshot resend(UUID sessionId, UUID actorId, long sequence, boolean authorized) {
            return last;
        }

        @Override
        public RebaseResult rebase(UUID sessionId, UUID actorId, Sha256 expectedBaseHash, boolean authorized) {
            rebaseCount.incrementAndGet();
            List<EditorOperation> ops = last == null ? List.of() : last.operations();
            return new RebaseResult(
                    expectedBaseHash,
                    ops,
                    List.of(),
                    last == null ? expectedBaseHash : last.draftRootHash()
            );
        }

        @Override
        public void publish(UUID sessionId, UUID actorId, Sha256 draftRootHash, boolean authorized) {
            publishCount.incrementAndGet();
        }
    }
}