package com.phasetranscrystal.fpsmatch.common.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.common.client.event.FPSMClientResetEvent;
import com.phasetranscrystal.fpsmatch.common.client.minimap.hud.GlobalHudCatalog;
import com.phasetranscrystal.fpsmatch.common.client.minimap.hud.HudRenderContext;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.ForgeMinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapViewerPose;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2.Ldlib2MinimapHudPresentation;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.IHudRenderer;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudFlexibleRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudSafeAreaResolution;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudSafeAreaRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class FPSMGameHudManager implements IGuiOverlay {
    private static final String MINIMAP_GLOBAL_ID = "fpsmatch:minimap";
    public static boolean enable = true;
    public static final FPSMGameHudManager INSTANCE = new FPSMGameHudManager();
    private final Map<String, List<IHudRenderer>> gameHudMap = Maps.newHashMap();
    private final GlobalHudCatalog globalHudCatalog = new GlobalHudCatalog();
    private ClientMinimapServices minimapServices;
    private Ldlib2MinimapHudPresentation minimapHud;
    private MinimapRenderFrame minimapRenderFrame;

    public FPSMGameHudManager() {
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        String gameType = data.getCurrentGameType();
        boolean isSpectator = data.isSpectator();
        if (enable && INSTANCE.gameHudMap.containsKey(gameType) && !isSpectator) {
            INSTANCE.gameHudMap.get(gameType)
                    .forEach(overlay -> overlay.onRenderGuiOverlayPre(event));
        }
    }

    public static boolean shouldRender() {
        return enable && FPSMClient.getGlobalData().isInGame();
    }

    public void registerHud(String gameType, IHudRenderer overlay) {
        gameHudMap.computeIfAbsent(gameType, k -> new ArrayList<>()).add(overlay);
    }

    /**
     * Stable-ID global HUD registration (capability-aware via predicate).
     * Nested registration during render is rejected by {@link GlobalHudCatalog}.
     */
    public void registerGlobalHud(
            String id,
            Predicate<HudRenderContext> predicate,
            Consumer<HudRenderContext> renderer
    ) {
        globalHudCatalog.registerGlobalHud(id, predicate, renderer);
    }

    public void registerResolvedGlobalHud(
            String id,
            Predicate<HudRenderContext> predicate,
            BiConsumer<HudRenderContext, HudSafeAreaResolution> renderer
    ) {
        globalHudCatalog.registerResolvedGlobalHud(id, predicate, renderer);
    }

    public void registerSafeAreaContributor(
            String id,
            int priority,
            BiConsumer<HudSafeAreaRegistry, HudRenderContext> contributor
    ) {
        globalHudCatalog.registerSafeAreaContributor(id, priority, contributor);
    }

    public GlobalHudCatalog globalHudCatalog() {
        return globalHudCatalog;
    }

    public synchronized void installMinimap(ClientMinimapServices services) {
        Objects.requireNonNull(services, "services");
        if (minimapHud != null) {
            return;
        }
        minimapServices = services;
        minimapHud = Ldlib2MinimapHudPresentation.create(services);
        registerSafeAreaContributor(
                HudFlexibleRequest.MINIMAP_HUD_ID,
                HudFlexibleRequest.MINIMAP_DEFAULT_PRIORITY,
                (registry, context) -> {
                    if (!eligible(context)) {
                        return;
                    }
                    MinimapClientSettings settings = requireMinimapFrame().settings();
                    registry.contributeFlexible(new HudFlexibleRequest(
                            HudFlexibleRequest.MINIMAP_HUD_ID,
                            settings.safeAreaPriority(),
                            settings.preferredSize(),
                            settings.minSize(),
                            Math.max(settings.marginX(), settings.marginY()),
                            settings.anchor()
                    ));
                }
        );
        registerResolvedGlobalHud(
                MINIMAP_GLOBAL_ID,
                FPSMGameHudManager::eligible,
                this::renderMinimap
        );
    }

    public synchronized Ldlib2MinimapHudPresentation minimapHudPresentation() {
        return minimapHud;
    }

    @SubscribeEvent
    public static void onMinimapLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        INSTANCE.resetMinimapPresentation();
    }

    @SubscribeEvent
    public static void onMinimapLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.resetMinimapPresentation();
    }

    @SubscribeEvent
    public static void onMinimapReset(FPSMClientResetEvent event) {
        INSTANCE.resetMinimapPresentation();
    }

    public void onMinimapResourceReload() {
        runOnRenderThread(() -> {
            if (minimapHud != null) {
                minimapHud.reset();
            }
            if (minimapServices != null) {
                minimapServices.reloadResources();
            }
        });
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        String gameType = data.getCurrentGameType();
        boolean isSpectator = data.isSpectator();

        // Existing game-type HUD behavior retained.
        if (enable && gameHudMap.containsKey(gameType)) {
            gameHudMap.get(gameType)
                    .forEach(overlay ->
                            overlay.render(gui, guiGraphics, partialTick, screenWidth, screenHeight, isSpectator));
        }

        ClientMinimapServices services = minimapServices;
        if (services == null) {
            return;
        }
        services.runtime().currentGeneration().ifPresent(generation -> {
            MinimapClientSettings settings = ForgeMinimapClientSettings.read(
                    FPSMConfig.client
            );
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 position = camera.getPosition();
            minimapRenderFrame = new MinimapRenderFrame(
                    gui,
                    guiGraphics,
                    partialTick,
                    screenWidth,
                    screenHeight,
                    settings,
                    new MinimapViewerPose(
                            position.x,
                            position.y,
                            position.z,
                            camera.getYRot()
                    )
            );
            try {
                globalHudCatalog.renderRegistered(
                        generation.mapKey(),
                        screenWidth,
                        screenHeight,
                        new HudRenderContext(
                                services.hasActiveScope(
                                        WireIdentity.Scope.MATCH_HUD
                                ),
                                enable,
                                settings.enabled(),
                                gameType,
                                isSpectator,
                                services.hasActiveScope(
                                        WireIdentity.Scope.TACTICAL_SCREEN
                                )
                        ),
                        ignored -> {
                        }
                );
            } finally {
                minimapRenderFrame = null;
            }
        });
    }

    /**
     * Pure two-phase entry used by minimap integration / unit tests.
     * Full client path will supply MapKey + capability flags from runtime state.
     */
    public void renderRegistered(
            MapKey mapKey,
            int screenWidth,
            int screenHeight,
            HudRenderContext context,
            Consumer<String> renderedIds
    ) {
        globalHudCatalog.renderRegistered(mapKey, screenWidth, screenHeight, context, renderedIds);
    }

    private void renderMinimap(
            HudRenderContext context,
            HudSafeAreaResolution resolution
    ) {
        MinimapRenderFrame frame = requireMinimapFrame();
        resolution.placement(HudFlexibleRequest.MINIMAP_HUD_ID)
                .filter(placement -> !placement.hidden())
                .ifPresent(placement -> minimapHud.render(
                        frame.gui(),
                        frame.graphics(),
                        frame.partialTick(),
                        frame.screenWidth(),
                        frame.screenHeight(),
                        placement,
                        frame.viewer(),
                        frame.settings()
                ));
    }

    private void resetMinimapPresentation() {
        runOnRenderThread(() -> {
            if (minimapHud != null) {
                minimapHud.reset();
            }
        });
    }

    private static void runOnRenderThread(Runnable action) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            action.run();
        } else {
            Minecraft.getInstance().execute(action);
        }
    }

    private MinimapRenderFrame requireMinimapFrame() {
        return Objects.requireNonNull(
                minimapRenderFrame, "minimap render frame"
        );
    }

    private static boolean eligible(HudRenderContext context) {
        return context.minimapHudVisible();
    }

    private record MinimapRenderFrame(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            MinimapClientSettings settings,
            MinimapViewerPose viewer
    ) {
    }
}
