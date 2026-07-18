package com.phasetranscrystal.fpsmatch.common.client.minimap.editor;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.DraftSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorCommandLog;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.EditorSessionGateway;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.PendingOperationQueue;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.command.RebaseResult;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapEditorControllerTest {
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void switchesToolsColorBrushSelectionAndCanvasCamera() {
        MinimapEditorController controller = openController(new RecordingGateway());

        controller.selectTool(EditorTool.ERASER);
        assertEquals(EditorTool.ERASER, controller.toolState().tool());

        controller.setBrushSize(12);
        controller.setColorArgb(0xFF112233);
        assertEquals(12, controller.toolState().brushSize());
        assertEquals(0xFF112233, controller.toolState().colorArgb());

        controller.setSelectionMode(SelectionMode.POLYGON);
        assertEquals(SelectionMode.POLYGON, controller.toolState().selectionMode());

        controller.panBy(8.0, -4.0);
        controller.zoomBy(0.5);
        assertEquals(8.0, controller.canvasState().panX(), 1e-9);
        assertEquals(-4.0, controller.canvasState().panY(), 1e-9);
        assertEquals(1.5, controller.canvasState().zoom(), 1e-9);
    }

    @Test
    void layerAndFloorActionsMarkDirtyAndSupportUndoRedoLocally() {
        MinimapEditorController controller = openController(new RecordingGateway());
        String paint = controller.document().createLayer(
                "ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        controller.selectLayer("ground", paint);
        controller.setLayerOpacity(0.4);
        assertTrue(controller.isDirty());
        assertTrue(controller.canUndo());
        controller.undo();
        assertTrue(controller.canRedo());
        controller.redo();
        assertEquals(0.4, controller.document().layer("ground", paint).opacity(), 1e-9);
    }

    @Test
    void shortcutsAreGatedWhenTextFieldFocused() {
        MinimapEditorController controller = openController(new RecordingGateway());
        controller.setTextFieldFocused(true);
        assertFalse(controller.handleShortcut(EditorShortcut.UNDO));
        controller.setTextFieldFocused(false);
        assertTrue(controller.handleShortcut(EditorShortcut.UNDO) || !controller.canUndo());
    }

    @Test
    void backgroundTasksCanBeCancelledAndReportRunningState() throws Exception {
        MinimapEditorController controller = openController(new RecordingGateway());
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        EditorTaskHandle handle = controller.scheduleBackground("import-png", () -> {
            started.set(true);
            entered.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            cancelled.set(true);
        });
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS), "background task should start");
        assertTrue(controller.taskScheduler().isRunning(handle.taskId()));
        handle.cancel();
        controller.taskScheduler().awaitIdle(1000);
        assertFalse(controller.taskScheduler().isRunning(handle.taskId()));
        assertTrue(started.get());
    }

    @Test
    void reconnectSavesDraftRebasesAndPublishesThroughGateway() {
        RecordingGateway gateway = new RecordingGateway();
        MinimapEditorController controller = openController(gateway);
        String paint = controller.document().createLayer(
                "ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        controller.selectLayer("ground", paint);
        controller.setLayerOpacity(0.7);

        controller.saveDraft();
        assertEquals(1, gateway.applyCount.get());
        assertTrue(controller.ackCursor() >= 1L);

        controller.onReconnect();
        assertEquals(1, gateway.rebaseCount.get());

        controller.publish();
        assertEquals(1, gateway.publishCount.get());
        assertFalse(controller.isDirty());
    }

    @Test
    void publishFailureIsRecoverableAndDoesNotClearDirty() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failPublish.set(true);
        MinimapEditorController controller = openController(gateway);
        String paint = controller.document().createLayer(
                "ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        controller.selectLayer("ground", paint);
        controller.setLayerOpacity(0.3);
        assertThrows(EditorCommandException.class, controller::publish);
        assertTrue(controller.isDirty());
        assertEquals(EditorStatus.ERROR, controller.status());
    }

    @Test
    void closeWithUnsavedRequiresExplicitChoiceAndNeverAutosaves() {
        RecordingGateway gateway = new RecordingGateway();
        MinimapEditorController controller = openController(gateway);
        String paint = controller.document().createLayer(
                "ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        controller.selectLayer("ground", paint);
        controller.setLayerOpacity(0.2);

        assertEquals(CloseDecision.NEED_CHOICE, controller.requestClose());
        assertEquals(0, gateway.publishCount.get());
        assertEquals(0, gateway.applyCount.get()); // dirty local op not auto-applied on close

        controller.chooseClose(CloseChoice.DISCARD);
        assertTrue(controller.isClosed());
        assertEquals(0, gateway.publishCount.get());
    }

    @Test
    void rejectsUnauthorizedPublishPath() {
        RecordingGateway gateway = new RecordingGateway();
        MinimapEditorController controller = openController(gateway);
        controller.setServerAuthorized(false);
        assertThrows(EditorCommandException.class, controller::publish);
        assertEquals(0, gateway.publishCount.get());
    }

    private static MinimapEditorController openController(EditorSessionGateway gateway) {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 16, "ground", DisplayLabel.literal("Ground"));
        EditorCommandLog log = EditorCommandLog.empty(hash("base"));
        return MinimapEditorController.open(
                SESSION,
                ACTOR,
                document,
                log,
                gateway,
                true
        );
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingGateway implements EditorSessionGateway {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final AtomicInteger rebaseCount = new AtomicInteger();
        private final AtomicInteger publishCount = new AtomicInteger();
        private final AtomicBoolean failPublish = new AtomicBoolean();
        private final AtomicReference<DraftSnapshot> last = new AtomicReference<>();
        private long ack;

        @Override
        public DraftSnapshot apply(UUID sessionId, UUID actorId, EditorCommand command, boolean authorized) {
            applyCount.incrementAndGet();
            ack = command.sequence();
            DraftSnapshot snapshot = new DraftSnapshot(
                    sessionId,
                    ack,
                    command.baseRootHash(),
                    command.resultingRootHash(),
                    List.of(command.operation())
            );
            last.set(snapshot);
            return snapshot;
        }

        @Override
        public DraftSnapshot resend(UUID sessionId, UUID actorId, long sequence, boolean authorized) {
            return last.get();
        }

        @Override
        public RebaseResult rebase(UUID sessionId, UUID actorId, Sha256 expectedBaseHash, boolean authorized) {
            rebaseCount.incrementAndGet();
            DraftSnapshot snapshot = last.get();
            List<EditorOperation> ops = snapshot == null ? List.of() : snapshot.operations();
            return new RebaseResult(expectedBaseHash, ops, List.of(),
                    snapshot == null ? expectedBaseHash : snapshot.draftRootHash());
        }

        @Override
        public void publish(UUID sessionId, UUID actorId, Sha256 draftRootHash, boolean authorized) {
            if (failPublish.get()) {
                throw new EditorCommandException("publish failed");
            }
            publishCount.incrementAndGet();
        }
    }
}