package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapTextureResolver;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MarkerPresentationResolver;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudAnchor;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Presentation adapter boundary for LDLib2 HUD widgets.
 * Controllers/renderers remain free of Widget / GuiGraphics types.
 * Concrete Widget tree construction lives in client-only LDLib2 UI code that consumes layout + frame.
 */
public final class Ldlib2MinimapHudAdapter implements ModularHudLayer {
    private final MinimapHudWidgetCatalog catalog = MinimapHudWidgetCatalog.defaultCatalog();
    private final Set<String> boundIds = new HashSet<>();
    private final UIElement root;
    private final Ldlib2MinimapCanvasElement canvas;
    private final ModularUI modularUI;
    private MinimapFrame lastFrame;

    public Ldlib2MinimapHudAdapter() {
        this(
                textureKey -> Optional.empty(),
                new MarkerPresentationResolver(Optional::empty, ignored -> false)
        );
    }

    public Ldlib2MinimapHudAdapter(MinimapTextureResolver textures) {
        this(
                textures,
                new MarkerPresentationResolver(Optional::empty, ignored -> false)
        );
    }

    public Ldlib2MinimapHudAdapter(
            MinimapTextureResolver textures,
            MarkerPresentationResolver markerPresentations
    ) {
        Objects.requireNonNull(textures, "textures");
        Objects.requireNonNull(markerPresentations, "markerPresentations");
        root = element(MinimapHudWidgetCatalog.ROOT);
        root.setAllowHitTest(false);
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        canvas = new Ldlib2MinimapCanvasElement(
                MinimapHudWidgetCatalog.CANVAS,
                textures,
                markerPresentations,
                false
        );
        canvas.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        UIElement configPreview = element(MinimapHudWidgetCatalog.CONFIG_PREVIEW)
                .setVisible(false);
        UIElement placeholder = element(MinimapHudWidgetCatalog.PLACEHOLDER)
                .setVisible(false);
        UIElement compass = element(MinimapHudWidgetCatalog.COMPASS)
                .setVisible(false);
        root.addChildren(canvas, configPreview, placeholder, compass);
        modularUI = ModularUI.of(UI.of(root));
    }

    public MinimapHudWidgetCatalog catalog() {
        return catalog;
    }

    public void bind(List<String> boundWidgetIds) {
        Objects.requireNonNull(boundWidgetIds, "boundWidgetIds");
        Set<String> required = new HashSet<>(catalog.ids());
        Set<String> provided = new HashSet<>(boundWidgetIds);
        if (!provided.containsAll(required)) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(provided);
            throw new IllegalStateException("Missing LDLib2 HUD widget bindings: " + missing);
        }
        for (String id : provided) {
            if (!required.contains(id)) {
                throw new IllegalStateException("Unknown LDLib2 HUD widget binding: " + id);
            }
        }
        boundIds.clear();
        boundIds.addAll(required);
    }

    public boolean isBound() {
        return boundIds.containsAll(catalog.ids());
    }

    public MinimapHudLayoutModel layout(MinimapClientSettings settings, int screenWidth, int screenHeight, int size) {
        Objects.requireNonNull(settings, "settings");
        MinimapClientSettings clamped = settings.clamp();
        int marginX = clamped.marginX();
        int marginY = clamped.marginY();
        int resolved = Math.max(clamped.minSize(), Math.min(clamped.preferredSize(), size));
        HudAnchor anchor = clamped.anchor();
        int x;
        int y;
        switch (anchor) {
            case TOP_LEFT -> {
                x = marginX;
                y = marginY;
            }
            case TOP_RIGHT -> {
                x = screenWidth - marginX - resolved;
                y = marginY;
            }
            case BOTTOM_LEFT -> {
                x = marginX;
                y = screenHeight - marginY - resolved;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - marginX - resolved;
                y = screenHeight - marginY - resolved;
            }
            default -> throw new IllegalStateException("Unexpected anchor: " + anchor);
        }
        return new MinimapHudLayoutModel(x, y, resolved, resolved);
    }

    public void present(MinimapFrame frame) {
        if (!isBound()) {
            throw new IllegalStateException("LDLib2 HUD adapter is not fully bound");
        }
        this.lastFrame = Objects.requireNonNull(frame, "frame");
        canvas.present(frame);
    }

    public MinimapFrame lastFrame() {
        return lastFrame;
    }

    public Ldlib2MinimapCanvasElement canvasElement() {
        return canvas;
    }

    public void place(MinimapHudLayoutModel placement) {
        Objects.requireNonNull(placement, "placement");
        canvas.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(placement.x())
                .top(placement.y())
                .width(placement.width())
                .height(placement.height()));
    }

    @Override
    public ModularUI getModularUI() {
        return modularUI;
    }

    private static UIElement element(String id) {
        return new UIElement().setId(id);
    }
}
