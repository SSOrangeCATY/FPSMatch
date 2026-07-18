package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.BoundedPngDecoder;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.ClientMinimapHudPresentationService;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinecraftMinimapTextureManager;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapTileUploadQueue;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapViewerPose;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapTextureResolver;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MarkerPresentationResolver;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudPlacement;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.ScreenRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.Objects;
import java.util.Optional;

public final class Ldlib2MinimapHudPresentation {
    private static final int MAX_PENDING_TILE_UPLOADS = 16;

    private final ClientMinimapHudPresentationService frames;
    private final Ldlib2MinimapHudAdapter adapter;
    private final MarkerPresentationResolver markerPresentations;

    private Ldlib2MinimapHudPresentation(
            ClientMinimapHudPresentationService frames,
            Ldlib2MinimapHudAdapter adapter,
            MarkerPresentationResolver markerPresentations
    ) {
        this.frames = Objects.requireNonNull(frames, "frames");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.markerPresentations = Objects.requireNonNull(
                markerPresentations, "markerPresentations"
        );
        this.adapter.bind(this.adapter.catalog().ids());
    }

    public static Ldlib2MinimapHudPresentation create(
            ClientMinimapServices services
    ) {
        Objects.requireNonNull(services, "services");
        MinecraftMinimapTextureManager textures =
                MinecraftMinimapTextureManager.createDefault(
                        services.runtime()::currentGeneration
                );
        MinimapTileUploadQueue uploads = new MinimapTileUploadQueue(
                new BoundedPngDecoder(
                        MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES,
                        MinimapHardLimits.MAX_DECODED_TILE_BYTES
                ),
                Util.backgroundExecutor(),
                task -> Minecraft.getInstance().execute(task),
                services.runtime()::currentGeneration,
                textures,
                MAX_PENDING_TILE_UPLOADS
        );
        ClientMinimapHudPresentationService frames =
                new ClientMinimapHudPresentationService(
                        services.runtime()::currentGeneration,
                        services::activeRuntime,
                        services.markerStore()::markers,
                        uploads,
                        textures
                );
        MarkerPresentationResolver markerPresentations =
                new MarkerPresentationResolver(
                        services.runtime()::currentGeneration,
                        textureId -> Minecraft.getInstance()
                                .getResourceManager()
                                .getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                        textureId.namespace(), textureId.path()
                                ))
                                .isPresent()
                );
        return new Ldlib2MinimapHudPresentation(
                frames,
                new Ldlib2MinimapHudAdapter(textures, markerPresentations),
                markerPresentations
        );
    }

    public boolean render(
            GuiGraphicsExtractor graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            HudPlacement placement,
            MinimapViewerPose viewer,
            MinimapClientSettings settings
    ) {
        Objects.requireNonNull(placement, "placement");
        if (placement.hidden()) {
            return false;
        }
        ScreenRect rect = placement.rect().orElseThrow();
        return frames.prepareFrame(
                viewer, settings, rect.width(), rect.height()
        ).map(frame -> {
            adapter.place(new MinimapHudLayoutModel(
                    rect.x(), rect.y(), rect.width(), rect.height()
            ));
            adapter.present(frame);
            adapter.render(graphics, DeltaTracker.ZERO);
            return true;
        }).orElse(false);
    }

    public Optional<MinimapFrame> prepareFrame(
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        return frames.prepareFrame(
                viewer, settings, viewportWidth, viewportHeight
        );
    }

    public Optional<TacticalMapPresentation> prepareTactical(
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        return frames.prepareTactical(viewer, settings, state);
    }

    public MinimapTextureResolver textureResolver() {
        return adapter.canvasElement().textures();
    }

    public MarkerPresentationResolver markerPresentationResolver() {
        return markerPresentations;
    }

    public void reset() {
        frames.reset();
        markerPresentations.reset();
    }
}
