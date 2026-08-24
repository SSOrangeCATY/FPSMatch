package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.platform.NativeImage;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorCanvasGesture;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorCanvasState;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorCanvasTransform;
import com.ptcrys.fpsmatch.common.client.minimap.editor.MinimapEditorController;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessiblePanel;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorTileCompositor;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

/** Interactive, clipped preview for the editor's live tile-backed document. */
public final class Ldlib2EditorCanvasElement extends AccessiblePanel {
    private static final AtomicLong INSTANCE_SEQUENCE = new AtomicLong();
    private static final int CANVAS_BACKGROUND = 0xFF171A1F;
    private static final int CHECKER_LIGHT = 0xFF30343B;
    private static final int CHECKER_DARK = 0xFF252930;
    private static final int DOCUMENT_BORDER = 0xFF7A8492;
    private static final int CHECKER_EDGE = 16;
    private static final double WHEEL_ZOOM_STEP = 0.25;
    private static final double KEYBOARD_PAN_PIXELS = 24.0;

    private final MinimapEditorController controller;
    private final EditorCanvasGesture gesture;
    private final long instanceId = INSTANCE_SEQUENCE.incrementAndGet();
    private final Map<TileKey, CachedTexture> textures = new LinkedHashMap<>();
    private Consumer<String> errorListener = ignored -> {
    };
    private Runnable editListener = () -> {
    };
    private EditorDocument renderedDocument;

    public Ldlib2EditorCanvasElement(
            String id,
            MinimapEditorController controller
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.gesture = new EditorCanvasGesture(controller);
        setId(Objects.requireNonNull(id, "id"));
        setAccessibleName(Component.translatable("gui.fpsm.minimap.editor.canvas.name"));
        setAccessibleHint(() -> Component.translatable(
                "gui.fpsm.minimap.editor.canvas.hint"
        ));
        setAllowHitTest(true);
        setOverflowVisible(false);
        style(style -> style.overflowClip(IGuiTexture.EMPTY));
        bindInput();
    }

    public void setErrorListener(Consumer<String> errorListener) {
        this.errorListener = Objects.requireNonNull(errorListener, "errorListener");
    }

    public void setEditListener(Runnable editListener) {
        this.editListener = Objects.requireNonNull(editListener, "editListener");
    }

    public void setEditingEnabled(boolean editingEnabled) {
        gesture.setEditingEnabled(editingEnabled);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        int viewportX = floor(getPositionX());
        int viewportY = floor(getPositionY());
        int viewportWidth = Math.max(0, ceil(getSizeWidth()));
        int viewportHeight = Math.max(0, ceil(getSizeHeight()));
        if (viewportWidth == 0 || viewportHeight == 0 || controller.isClosed()) {
            return;
        }

        EditorDocument document = controller.document();
        if (renderedDocument != document) {
            releaseTextures();
            renderedDocument = document;
            gesture.cancel();
        }
        String floorId = controller.selectedFloorId();
        if (floorId == null) {
            context.graphics.fill(
                    viewportX, viewportY,
                    viewportX + viewportWidth, viewportY + viewportHeight,
                    CANVAS_BACKGROUND
            );
            return;
        }

        try {
            context.graphics.fill(
                    viewportX, viewportY,
                    viewportX + viewportWidth, viewportY + viewportHeight,
                    CANVAS_BACKGROUND
            );
            drawDocumentBackground(
                    context, document, viewportX, viewportY, viewportWidth, viewportHeight
            );
            drawTiles(
                    context, document, floorId,
                    viewportX, viewportY, viewportWidth, viewportHeight
            );
            drawDocumentBorder(
                    context, document, viewportX, viewportY, viewportWidth, viewportHeight
            );
        } catch (RuntimeException failure) {
            reportFailure(failure);
        }
    }

    private void bindInput() {
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            try {
                if (gesture.begin(event.x, event.y, event.button, viewport())) {
                    startDrag(null, null);
                    event.stopPropagation();
                }
            } catch (RuntimeException failure) {
                gesture.cancel();
                reportFailure(failure);
            }
        });
        addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::dragGesture);
        addEventListener(UIEvents.DRAG_UPDATE, event -> {
            if (event.dragHandler == null) {
                dragGesture(event);
            }
        });
        addEventListener(UIEvents.MOUSE_UP, event -> finishGesture(event));
        addEventListener(UIEvents.DRAG_END, this::finishGesture);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            try {
                gesture.zoomBy(
                        event.deltaY * WHEEL_ZOOM_STEP,
                        event.x,
                        event.y,
                        viewport()
                );
            } catch (RuntimeException failure) {
                gesture.cancel();
                reportFailure(failure);
            }
            event.stopPropagation();
        });
        addEventListener(UIEvents.KEY_DOWN, event -> {
            if (!event.isCtrlDown() && !event.isAltDown()
                    && handleKeyboardKey(event.keyCode)) {
                event.stopPropagation();
            }
        });
    }

    /** Handles viewport-only keyboard equivalents while this canvas owns focus. */
    public boolean handleKeyboardKey(int keyCode) {
        if (!isFocused() || !isActive() || controller.isClosed()) {
            return false;
        }
        double pan = KEYBOARD_PAN_PIXELS / controller.canvasState().zoom();
        switch (keyCode) {
            case GLFW_KEY_LEFT, GLFW_KEY_A -> {
                gesture.cancel();
                controller.panBy(-pan, 0);
                return true;
            }
            case GLFW_KEY_RIGHT, GLFW_KEY_D -> {
                gesture.cancel();
                controller.panBy(pan, 0);
                return true;
            }
            case GLFW_KEY_UP, GLFW_KEY_W -> {
                gesture.cancel();
                controller.panBy(0, -pan);
                return true;
            }
            case GLFW_KEY_DOWN, GLFW_KEY_S -> {
                gesture.cancel();
                controller.panBy(0, pan);
                return true;
            }
            case GLFW_KEY_EQUAL, GLFW_KEY_KP_ADD -> {
                gesture.zoomBy(
                        WHEEL_ZOOM_STEP,
                        getPositionX() + getSizeWidth() * 0.5,
                        getPositionY() + getSizeHeight() * 0.5,
                        viewport()
                );
                return true;
            }
            case GLFW_KEY_MINUS, GLFW_KEY_KP_SUBTRACT -> {
                gesture.zoomBy(
                        -WHEEL_ZOOM_STEP,
                        getPositionX() + getSizeWidth() * 0.5,
                        getPositionY() + getSizeHeight() * 0.5,
                        viewport()
                );
                return true;
            }
            case GLFW_KEY_HOME -> {
                gesture.cancel();
                controller.resetCanvasView();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void dragGesture(UIEvent event) {
        if (!gesture.active()) {
            return;
        }
        try {
            gesture.drag(
                    event.x, event.y, event.deltaX, event.deltaY, viewport()
            );
        } catch (RuntimeException failure) {
            gesture.cancel();
            reportFailure(failure);
        }
        event.stopPropagation();
    }

    private void finishGesture(UIEvent event) {
        if (!gesture.active()) {
            return;
        }
        try {
            if (gesture.end(event.x, event.y, viewport())) {
                editListener.run();
            }
        } catch (RuntimeException failure) {
            gesture.cancel();
            reportFailure(failure);
        }
        event.stopPropagation();
    }

    private void drawDocumentBackground(
            GUIContext context,
            EditorDocument document,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
        ScreenRect documentRect = documentRect(document, viewportX, viewportY);
        int left = Math.max(viewportX, floor(documentRect.left()));
        int top = Math.max(viewportY, floor(documentRect.top()));
        int right = Math.min(viewportX + viewportWidth, ceil(documentRect.right()));
        int bottom = Math.min(viewportY + viewportHeight, ceil(documentRect.bottom()));
        for (int y = top; y < bottom; y += CHECKER_EDGE) {
            for (int x = left; x < right; x += CHECKER_EDGE) {
                int checkerX = Math.floorDiv(x - floor(documentRect.left()), CHECKER_EDGE);
                int checkerY = Math.floorDiv(y - floor(documentRect.top()), CHECKER_EDGE);
                int color = ((checkerX + checkerY) & 1) == 0
                        ? CHECKER_LIGHT : CHECKER_DARK;
                context.graphics.fill(
                        x, y,
                        Math.min(right, x + CHECKER_EDGE),
                        Math.min(bottom, y + CHECKER_EDGE),
                        color
                );
            }
        }
    }

    private void drawTiles(
            GUIContext context,
            EditorDocument document,
            String floorId,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
        EditorCanvasState state = controller.canvasState();
        int tileEdge = document.tileEdge();
        int tilesX = divideRoundUp(document.canvas().width(), tileEdge);
        int tilesY = divideRoundUp(document.canvas().height(), tileEdge);
        int firstTileX = clampTile((int) Math.floor(state.panX() / tileEdge), tilesX);
        int firstTileY = clampTile((int) Math.floor(state.panY() / tileEdge), tilesY);
        int lastTileX = clampTile((int) Math.floor(
                (state.panX() + viewportWidth / state.zoom()) / tileEdge
        ), tilesX);
        int lastTileY = clampTile((int) Math.floor(
                (state.panY() + viewportHeight / state.zoom()) / tileEdge
        ), tilesY);
        Set<TileKey> retained = new HashSet<>();
        if (lastTileX < firstTileX || lastTileY < firstTileY) {
            pruneTextures(retained);
            return;
        }

        for (int tileY = firstTileY; tileY <= lastTileY; tileY++) {
            for (int tileX = firstTileX; tileX <= lastTileX; tileX++) {
                TileKey key = new TileKey(floorId, tileX, tileY);
                EditorTileCompositor.CompositedTile tile = EditorTileCompositor.composite(
                        document, floorId, tileX, tileY
                );
                if (tile.transparent()) {
                    releaseTexture(key);
                    continue;
                }
                byte[] rgba = tile.rgba();
                CachedTexture texture = texture(key, tile.width(), tile.height(), rgba);
                retained.add(key);
                drawTile(
                        context, texture.location(), tile.width(), tile.height(),
                        tileX, tileY, tileEdge,
                        viewportX, viewportY, state
                );
            }
        }
        pruneTextures(retained);
    }

    private CachedTexture texture(
            TileKey key,
            int width,
            int height,
            byte[] rgba
    ) {
        String digest = Sha256Digest.of(rgba).value();
        CachedTexture cached = textures.get(key);
        if (cached != null && cached.digest().equals(digest)) {
            return cached;
        }
        if (cached != null) {
            Minecraft.getInstance().getTextureManager().release(cached.location());
        }
        ResourceLocation location = textureLocation(key);
        upload(location, width, height, rgba);
        CachedTexture replacement = new CachedTexture(location, digest);
        textures.put(key, replacement);
        return replacement;
    }

    private void drawTile(
            GUIContext context,
            ResourceLocation location,
            int textureWidth,
            int textureHeight,
            int tileX,
            int tileY,
            int tileEdge,
            int viewportX,
            int viewportY,
            EditorCanvasState state
    ) {
        double left = viewportX + (tileX * (double) tileEdge - state.panX()) * state.zoom();
        double top = viewportY + (tileY * (double) tileEdge - state.panY()) * state.zoom();
        int drawLeft = floor(left);
        int drawTop = floor(top);
        int drawRight = ceil(left + textureWidth * state.zoom());
        int drawBottom = ceil(top + textureHeight * state.zoom());
        context.graphics.blit(
                location,
                drawLeft,
                drawTop,
                Math.max(1, drawRight - drawLeft),
                Math.max(1, drawBottom - drawTop),
                0f,
                0f,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight
        );
    }

    private void drawDocumentBorder(
            GUIContext context,
            EditorDocument document,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
        ScreenRect rect = documentRect(document, viewportX, viewportY);
        int left = floor(rect.left());
        int top = floor(rect.top());
        int right = ceil(rect.right());
        int bottom = ceil(rect.bottom());
        context.graphics.fill(left, top, right, top + 1, DOCUMENT_BORDER);
        context.graphics.fill(left, bottom - 1, right, bottom, DOCUMENT_BORDER);
        context.graphics.fill(left, top, left + 1, bottom, DOCUMENT_BORDER);
        context.graphics.fill(right - 1, top, right, bottom, DOCUMENT_BORDER);
    }

    private ScreenRect documentRect(
            EditorDocument document,
            int viewportX,
            int viewportY
    ) {
        EditorCanvasState state = controller.canvasState();
        double left = viewportX - state.panX() * state.zoom();
        double top = viewportY - state.panY() * state.zoom();
        return new ScreenRect(
                left,
                top,
                left + document.canvas().width() * state.zoom(),
                top + document.canvas().height() * state.zoom()
        );
    }

    private EditorCanvasTransform.ViewportRect viewport() {
        return new EditorCanvasTransform.ViewportRect(
                getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight()
        );
    }

    private void reportFailure(RuntimeException failure) {
        String message = failure.getMessage();
        errorListener.accept(message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message);
    }

    @Override
    protected void onRemoved() {
        gesture.cancel();
        releaseTextures();
        renderedDocument = null;
        super.onRemoved();
    }

    private void pruneTextures(Set<TileKey> retained) {
        Iterator<Map.Entry<TileKey, CachedTexture>> iterator = textures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TileKey, CachedTexture> entry = iterator.next();
            if (!retained.contains(entry.getKey())) {
                Minecraft.getInstance().getTextureManager().release(entry.getValue().location());
                iterator.remove();
            }
        }
    }

    private void releaseTexture(TileKey key) {
        CachedTexture cached = textures.remove(key);
        if (cached != null) {
            Minecraft.getInstance().getTextureManager().release(cached.location());
        }
    }

    private void releaseTextures() {
        for (CachedTexture texture : textures.values()) {
            Minecraft.getInstance().getTextureManager().release(texture.location());
        }
        textures.clear();
    }

    private ResourceLocation textureLocation(TileKey key) {
        String floorDigest = Sha256Digest.of(
                key.floorId().getBytes(StandardCharsets.UTF_8)
        ).value().substring(0, 16);
        return new ResourceLocation(
                "fpsmatch",
                "minimap/editor/" + instanceId + "/" + floorDigest + "/"
                        + key.tileX() + "_" + key.tileY()
        );
    }

    private static void upload(
            ResourceLocation location,
            int width,
            int height,
            byte[] rgba
    ) {
        NativeImage image = new NativeImage(width, height, true);
        DynamicTexture texture = null;
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    int red = rgba[offset] & 0xff;
                    int green = rgba[offset + 1] & 0xff;
                    int blue = rgba[offset + 2] & 0xff;
                    int alpha = rgba[offset + 3] & 0xff;
                    image.setPixelRGBA(
                            x, y,
                            alpha << 24 | blue << 16 | green << 8 | red
                    );
                }
            }
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
        } catch (RuntimeException failure) {
            if (texture == null) {
                image.close();
            } else {
                texture.close();
            }
            throw failure;
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return Math.max(1, (value + divisor - 1) / divisor);
    }

    private static int clampTile(int tile, int tileCount) {
        if (tileCount <= 0 || tile >= tileCount) {
            return tileCount - 1;
        }
        return Math.max(0, tile);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private record TileKey(String floorId, int tileX, int tileY) {
    }

    private record CachedTexture(ResourceLocation location, String digest) {
    }

    private record ScreenRect(double left, double top, double right, double bottom) {
    }
}
