package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.ptcrys.fpsmatch.common.client.minimap.ClientMinimapSubscriptionCoordinator;
import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.BoundedPngDecoder;
import com.ptcrys.fpsmatch.common.client.minimap.render.ClientMinimapHudPresentationService;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinecraftMinimapTextureManager;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapTileUploadQueue;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapViewerPose;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.ptcrys.fpsmatch.common.client.minimap.render.MinimapTextureResolver;
import com.ptcrys.fpsmatch.common.client.minimap.render.MarkerPresentationResolver;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.hud.HudPlacement;
import com.ptcrys.fpsmatch.core.minimap.hud.ScreenRect;
import com.ptcrys.fpsmatch.core.minimap.view.MapDrawCommand;
import com.ptcrys.fpsmatch.core.minimap.view.PlaceholderKind;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class Ldlib2MinimapHudPresentation {
    private static final int MAX_PENDING_TILE_UPLOADS = 16;

    private final ClientMinimapHudPresentationService frames;
    private final Ldlib2MinimapHudAdapter adapter;
    private final MarkerPresentationResolver markerPresentations;
    private final Supplier<Optional<RuntimeGeneration>> currentGeneration;
    private final Supplier<ClientMinimapSubscriptionCoordinator.HudProjection>
            hudProjection;
    private volatile AcceptanceFrameState lastAcceptanceFrame;

    private Ldlib2MinimapHudPresentation(
            ClientMinimapHudPresentationService frames,
            Ldlib2MinimapHudAdapter adapter,
            MarkerPresentationResolver markerPresentations,
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            Supplier<ClientMinimapSubscriptionCoordinator.HudProjection>
                    hudProjection
    ) {
        this.frames = Objects.requireNonNull(frames, "frames");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.markerPresentations = Objects.requireNonNull(
                markerPresentations, "markerPresentations"
        );
        this.currentGeneration = Objects.requireNonNull(
                currentGeneration, "currentGeneration"
        );
        this.hudProjection = Objects.requireNonNull(
                hudProjection, "hudProjection"
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
                        textures,
                        () -> services.generatedMinimap().store().snapshot(),
                        services.generatedMinimap()::bindRuntime
                );
        MarkerPresentationResolver markerPresentations =
                new MarkerPresentationResolver(
                        services.runtime()::currentGeneration,
                        textureId -> Minecraft.getInstance()
                                .getResourceManager()
                                .getResource(Objects.requireNonNull(ResourceLocation.tryBuild(
                                        textureId.namespace(), textureId.path()
                                )))
                                .isPresent()
                );
        return new Ldlib2MinimapHudPresentation(
                frames,
                new Ldlib2MinimapHudAdapter(textures, markerPresentations),
                markerPresentations,
                services.runtime()::currentGeneration,
                services.subscriptions()::matchHudProjection
        );
    }

    public boolean render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            HudPlacement placement,
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            long frameSequence
    ) {
        Objects.requireNonNull(placement, "placement");
        lastAcceptanceFrame = null;
        if (placement.hidden()) {
            return false;
        }
        ClientMinimapSubscriptionCoordinator.HudProjection projection =
                hudProjection.get();
        if (!projection.visible()) {
            return false;
        }
        Optional<PlaceholderKind> forcedPlaceholder = switch (
                projection.state()
        ) {
            case HIDDEN, READY -> Optional.empty();
            case LOADING -> Optional.of(PlaceholderKind.LOADING);
            case ERROR -> Optional.of(PlaceholderKind.ERROR);
        };
        ScreenRect rect = placement.rect().orElseThrow();
        Optional<RuntimeGeneration> preparedGeneration =
                forcedPlaceholder.isEmpty()
                        ? currentGeneration.get()
                        : Optional.empty();
        return frames.prepareFrame(
                viewer,
                settings,
                rect.width(),
                rect.height(),
                forcedPlaceholder
        ).map(frame -> {
            adapter.place(new MinimapHudLayoutModel(
                    rect.x(), rect.y(), rect.width(), rect.height()
            ));
            adapter.present(frame);
            long drawSequenceBefore = adapter.canvasElement().drawReceipt()
                    .map(MinimapDrawReceipt::sequence)
                    .orElse(0L);
            adapter.render(
                    gui, graphics, partialTick, screenWidth, screenHeight
            );
            recordAcceptanceFrame(
                    preparedGeneration,
                    frame,
                    frameSequence,
                    drawSequenceBefore,
                    adapter.canvasElement().drawReceipt()
            );
            return true;
        }).orElse(false);
    }

    public Optional<AcceptanceFrameState> acceptanceFrameState(
            RuntimeGeneration expected,
            long frameSequence
    ) {
        Objects.requireNonNull(expected, "expected");
        AcceptanceFrameState observed = lastAcceptanceFrame;
        Optional<RuntimeGeneration> current = currentGeneration.get();
        if (observed == null || current.isEmpty()
                || !expected.equals(current.orElseThrow())
                || !expected.equals(observed.generation())
                || observed.frameSequence() != frameSequence) {
            return Optional.empty();
        }
        return Optional.of(observed);
    }

    private void recordAcceptanceFrame(
            Optional<RuntimeGeneration> preparedGeneration,
            MinimapFrame frame,
            long frameSequence,
            long drawSequenceBefore,
            Optional<MinimapDrawReceipt> drawReceipt
    ) {
        Optional<RuntimeGeneration> renderedGeneration = currentGeneration.get();
        boolean realFrame = frame.placeholder().isEmpty();
        if (preparedGeneration.isEmpty()
                || !preparedGeneration.equals(renderedGeneration)
                || !realFrame
                || !MinimapDrawReceipt.confirms(
                        drawReceipt, drawSequenceBefore, frame
                )) {
            return;
        }
        boolean hasTile = frame.commands().stream()
                .anyMatch(MapDrawCommand.Tile.class::isInstance);
        boolean hasMarker = frame.commands().stream()
                .anyMatch(MapDrawCommand.MarkerIcon.class::isInstance);
        if (hasTile && hasMarker) {
            lastAcceptanceFrame = new AcceptanceFrameState(
                    preparedGeneration.orElseThrow(),
                    frameSequence
            );
        }
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
        lastAcceptanceFrame = null;
        frames.reset();
        markerPresentations.reset();
    }

    public record AcceptanceFrameState(
            RuntimeGeneration generation,
            long frameSequence
    ) {
        public AcceptanceFrameState {
            Objects.requireNonNull(generation, "generation");
            if (frameSequence < 0L) {
                throw new IllegalArgumentException("frameSequence must be non-negative");
            }
        }
    }
}
