package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.ptcrys.fpsmatch.common.client.minimap.editor.LocalEditorSessionGateway;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.ptcrys.fpsmatch.common.client.net.FPSMClientPacketRegistrar;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandLog;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client entry points for the OP minimap editor UI.
 */
public final class MinimapEditorScreens {
    private static final int DEFAULT_CANVAS = 512;
    private static final int DEFAULT_TILE_EDGE = 128;

    private MinimapEditorScreens() {
    }

    public static void open(MapRoomDetail detail, Screen parent) {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(parent, "parent");
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !detail.summary().currentPlayerOp()) {
            return;
        }

        MapKey mapKey = new MapKey(detail.summary().gameType(), detail.summary().mapName());
        NamespacedId dimension = parseDimension(detail.summary().dimension());
        NamespacedId documentId = LocalEditorSessionGateway.documentIdFor(mapKey);
        UUID sessionId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.EDITOR,
                System.currentTimeMillis(),
                1L
        );

        Consumer<MinimapWireMessage> sender = message -> FPSMatch.sendMinimapToServer(
                UUID.randomUUID(),
                message
        );
        LocalEditorSessionGateway gateway = new LocalEditorSessionGateway(
                player.getUUID(),
                mapKey,
                dimension,
                documentId,
                sessionId,
                draftId,
                lease,
                sender,
                UUID::randomUUID
        );

        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(DEFAULT_CANVAS, DEFAULT_CANVAS),
                DEFAULT_TILE_EDGE,
                "ground",
                DisplayLabel.literal("Ground")
        );
        EditorCommandLog log = EditorCommandLog.empty(LocalEditorSessionGateway.emptyHash());
        MinimapEditorController controller = MinimapEditorController.open(
                sessionId,
                player.getUUID(),
                document,
                log,
                gateway,
                true
        );

        ClientMinimapServices services = FPSMClientPacketRegistrar.minimapServices();
        if (services != null) {
            services.attachEditorSessionListener(gateway::acceptServerSession);
            services.attachPublishResultListener(gateway::acceptPublishResult);
        }

        gateway.requestOpen(WireEditor.OpenMode.CREATE_EMPTY);
        minecraft.setScreen(new Ldlib2MinimapEditorScreen(
                controller,
                gateway,
                parent,
                mapKey
        ));
    }

    private static NamespacedId parseDimension(String raw) {
        try {
            return NamespacedId.parse(raw);
        } catch (RuntimeException ignored) {
            return NamespacedId.parse("minecraft:overworld");
        }
    }
}
