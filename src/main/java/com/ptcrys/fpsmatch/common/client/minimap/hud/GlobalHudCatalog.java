package com.ptcrys.fpsmatch.common.client.minimap.hud;

import com.ptcrys.fpsmatch.core.minimap.hud.HudSafeAreaRegistry;
import com.ptcrys.fpsmatch.core.minimap.hud.HudSafeAreaResolution;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Two-phase global HUD catalog: collect safe areas, resolve, then render.
 * Registration is locked while rendering to prevent nested registration.
 */
public final class GlobalHudCatalog {
    private final Map<String, GlobalHudSlot> globalSlots = new LinkedHashMap<>();
    private final Map<String, SafeAreaContributorSlot> safeAreaSlots = new LinkedHashMap<>();
    private final Map<String, Consumer<HudRenderContext>> gameTypeHooks = new LinkedHashMap<>();
    private final HudSafeAreaRegistry safeAreaRegistry = new HudSafeAreaRegistry();
    private boolean rendering;

    public synchronized void registerGlobalHud(
            String id,
            Predicate<HudRenderContext> predicate,
            Consumer<HudRenderContext> renderer
    ) {
        rejectIfRendering();
        Objects.requireNonNull(id, "id");
        if (globalSlots.containsKey(id)) {
            throw new IllegalStateException("duplicate global hud id: " + id);
        }
        globalSlots.put(id, new GlobalHudSlot(
                id, predicate, (context, resolution) -> renderer.accept(context)
        ));
    }

    public synchronized void registerResolvedGlobalHud(
            String id,
            Predicate<HudRenderContext> predicate,
            BiConsumer<HudRenderContext, HudSafeAreaResolution> renderer
    ) {
        rejectIfRendering();
        Objects.requireNonNull(id, "id");
        if (globalSlots.containsKey(id)) {
            throw new IllegalStateException("duplicate global hud id: " + id);
        }
        globalSlots.put(id, new GlobalHudSlot(id, predicate, renderer));
    }

    public synchronized void registerSafeAreaContributor(
            String id,
            int priority,
            BiConsumer<HudSafeAreaRegistry, HudRenderContext> contributor
    ) {
        rejectIfRendering();
        Objects.requireNonNull(id, "id");
        if (safeAreaSlots.containsKey(id)) {
            throw new IllegalStateException("duplicate safe-area contributor id: " + id);
        }
        safeAreaSlots.put(id, new SafeAreaContributorSlot(id, priority, contributor));
    }

    public synchronized void registerGameTypeHook(String gameType, Consumer<HudRenderContext> renderer) {
        rejectIfRendering();
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(renderer, "renderer");
        gameTypeHooks.computeIfAbsent(gameType, key -> renderer);
    }

    /**
     * Phase 1 collect contributors, resolve placements, phase 2 render eligible global slots then game-type hook.
     */
    public synchronized HudSafeAreaResolution renderRegistered(
            MapKey mapKey,
            int screenWidth,
            int screenHeight,
            HudRenderContext context,
            Consumer<String> renderedIds
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(renderedIds, "renderedIds");
        if (rendering) {
            throw new IllegalStateException("nested render is not allowed");
        }
        rendering = true;
        try {
            safeAreaRegistry.beginFrame(mapKey, screenWidth, screenHeight);
            List<SafeAreaContributorSlot> contributors = new ArrayList<>(safeAreaSlots.values());
            contributors.sort(Comparator
                    .comparingInt(SafeAreaContributorSlot::priority).reversed()
                    .thenComparing(SafeAreaContributorSlot::id));
            for (SafeAreaContributorSlot slot : contributors) {
                slot.contributor().accept(safeAreaRegistry, context);
            }
            HudSafeAreaResolution resolution = safeAreaRegistry.resolve();

            for (GlobalHudSlot slot : globalSlots.values()) {
                if (slot.predicate().test(context)) {
                    slot.renderer().accept(context, resolution);
                    renderedIds.accept(slot.id());
                }
            }
            Consumer<HudRenderContext> gameHook = gameTypeHooks.get(context.gameType());
            if (gameHook != null) {
                gameHook.accept(context);
                renderedIds.accept("game:" + context.gameType());
            }
            return resolution;
        } finally {
            rendering = false;
        }
    }

    public boolean isRendering() {
        return rendering;
    }

    private void rejectIfRendering() {
        if (rendering) {
            throw new IllegalStateException("hud registration is locked during render");
        }
    }
}
