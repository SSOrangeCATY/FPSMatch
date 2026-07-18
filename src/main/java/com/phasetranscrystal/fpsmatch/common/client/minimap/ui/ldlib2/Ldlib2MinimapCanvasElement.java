package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.GuiGraphicsMinimapDrawBackend;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinecraftGuiGraphicsMinimapDrawTarget;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MarkerPresentationResolver;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapTextureResolver;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;

import java.util.Objects;

public final class Ldlib2MinimapCanvasElement extends UIElement {
    private final MinimapTextureResolver textures;
    private final MarkerPresentationResolver markerPresentations;
    private final SDFRectTexture clip = SDFRectTexture.of(0xFFFFFFFF);
    private volatile MinimapFrame frame;

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
        setAllowHitTest(interactive);
        setOverflowVisible(false);
        style(style -> style.overflowClip(clip));
    }

    public void present(MinimapFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        clip.setRadius(frame.shape() == ShapeMode.CIRCLE ? 10_000f : 0f);
    }

    public MinimapFrame frame() {
        return frame;
    }

    public MinimapTextureResolver textures() {
        return textures;
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
    }

    private static MarkerPresentationResolver emptyMarkerPresentations() {
        return new MarkerPresentationResolver(
                java.util.Optional::empty,
                ignored -> false
        );
    }
}
