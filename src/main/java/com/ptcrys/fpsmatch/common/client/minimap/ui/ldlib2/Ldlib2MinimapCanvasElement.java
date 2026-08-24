package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessiblePanel;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.ptcrys.fpsmatch.common.client.minimap.render.GuiGraphicsMinimapDrawBackend;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinecraftGuiGraphicsMinimapDrawTarget;
import com.ptcrys.fpsmatch.common.client.minimap.render.MarkerPresentationResolver;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapTextureResolver;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.Optional;

public final class Ldlib2MinimapCanvasElement extends AccessiblePanel {
    private final MinimapTextureResolver textures;
    private final MarkerPresentationResolver markerPresentations;
    private final SDFRectTexture circleClip = SDFRectTexture.of(0xFFFFFFFF);
    private volatile MinimapFrame frame;
    private long drawSequence;
    private volatile MinimapDrawReceipt drawReceipt;

    public Ldlib2MinimapCanvasElement(
            String id,
            MinimapTextureResolver textures
    ) {
        this(id, textures, emptyMarkerPresentations(), false);
    }

    public Ldlib2MinimapCanvasElement(
            String id,
            MinimapTextureResolver textures,
            boolean interactive
    ) {
        this(id, textures, emptyMarkerPresentations(), interactive);
    }

    public Ldlib2MinimapCanvasElement(
            String id,
            MinimapTextureResolver textures,
            MarkerPresentationResolver markerPresentations,
            boolean interactive
    ) {
        this.textures = Objects.requireNonNull(textures, "textures");
        this.markerPresentations = Objects.requireNonNull(
                markerPresentations, "markerPresentations"
        );
        setId(Objects.requireNonNull(id, "id"));
        setFocusable(interactive);
        setAccessibleName(Component.translatable(
                "gui.fpsm.minimap.tactical.canvas.name"
        ));
        setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.tactical.canvas.hint"
        ));
        setAllowHitTest(interactive);
        setOverflowVisible(false);
        style(style -> style.overflowClip(IGuiTexture.EMPTY));
    }

    public void present(MinimapFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        if (Ldlib2MinimapCanvasClipPolicy.usesCircularClip(frame.shape())) {
            // LDLib2 binds this clip during the style update.
            circleClip.setRadius(
                    Ldlib2MinimapCanvasClipPolicy.circularRadius(frame)
            );
            style(style -> style.overflowClip(circleClip));
        } else {
            style(style -> style.overflowClip(IGuiTexture.EMPTY));
        }
    }

    /** Clears a stale projection when the runtime authority is no longer usable. */
    public void clearFrame() {
        frame = null;
        drawReceipt = null;
        style(style -> style.overflowClip(IGuiTexture.EMPTY));
    }

    public MinimapFrame frame() {
        return frame;
    }

    public MinimapTextureResolver textures() {
        return textures;
    }

    Optional<MinimapDrawReceipt> drawReceipt() {
        return Optional.ofNullable(drawReceipt);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        MinimapFrame current = frame;
        if (current == null || getSizeWidth() <= 0 || getSizeHeight() <= 0) {
            return;
        }
        new GuiGraphicsMinimapDrawBackend(
                new MinecraftGuiGraphicsMinimapDrawTarget(
                        context.graphics, textures
                ),
                getPositionX(),
                getPositionY(),
                getSizeWidth(),
                getSizeHeight(),
                markerPresentations
        ).submit(current);
        long nextSequence = drawSequence + 1L;
        MinimapDrawReceipt completed = new MinimapDrawReceipt(
                current, nextSequence
        );
        drawSequence = nextSequence;
        drawReceipt = completed;
    }

    private static MarkerPresentationResolver emptyMarkerPresentations() {
        return new MarkerPresentationResolver(
                java.util.Optional::empty,
                ignored -> false
        );
    }
}
