package com.phasetranscrystal.fpsmatch.common.client.minimap.hud;

import com.phasetranscrystal.fpsmatch.core.minimap.hud.ScreenRect;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudAnchor;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudFlexibleRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.hud.HudPlacement;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalHudRegistrationTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");

    @Test
    void registersStableGlobalAndGameTypeHudWithoutNestedRenderRegistration() {
        GlobalHudCatalog catalog = new GlobalHudCatalog();
        AtomicInteger renderCount = new AtomicInteger();
        AtomicBoolean nestedAttempted = new AtomicBoolean();

        catalog.registerGlobalHud(
                "fpsmatch:minimap",
                ctx -> ctx.capabilityPresent() && ctx.globalEnabled() && ctx.minimapEnabled(),
                ctx -> {
                    renderCount.incrementAndGet();
                    assertThrows(IllegalStateException.class, () ->
                            catalog.registerGlobalHud("fpsmatch:nested", c -> true, c -> nestedAttempted.set(true)));
                }
        );
        catalog.registerGameTypeHook("cs", ctx -> renderCount.incrementAndGet());

        List<String> rendered = new ArrayList<>();
        catalog.renderRegistered(
                MAP,
                400,
                300,
                new HudRenderContext(true, true, true, "cs", false),
                rendered::add
        );
        assertTrue(rendered.contains("fpsmatch:minimap"));
        assertTrue(rendered.contains("game:cs"));
        assertEquals(2, renderCount.get());
        assertFalse(nestedAttempted.get());
    }

    @Test
    void globalHudPredicateGatesCapabilityAndSwitches() {
        GlobalHudCatalog catalog = new GlobalHudCatalog();
        AtomicInteger renders = new AtomicInteger();
        catalog.registerGlobalHud(
                "fpsmatch:minimap",
                ctx -> ctx.capabilityPresent() && ctx.globalEnabled() && ctx.minimapEnabled(),
                ctx -> renders.incrementAndGet()
        );

        catalog.renderRegistered(MAP, 400, 300, new HudRenderContext(false, true, true, "cs", false), id -> {});
        assertEquals(0, renders.get());
        catalog.renderRegistered(MAP, 400, 300, new HudRenderContext(true, false, true, "cs", false), id -> {});
        assertEquals(0, renders.get());
        catalog.renderRegistered(MAP, 400, 300, new HudRenderContext(true, true, false, "cs", false), id -> {});
        assertEquals(0, renders.get());
        catalog.renderRegistered(MAP, 400, 300, new HudRenderContext(true, true, true, "cs", false), id -> {});
        assertEquals(1, renders.get());
    }

    @Test
    void tacticalScreenPausesOnlyTheMinimapHud() {
        GlobalHudCatalog catalog = new GlobalHudCatalog();
        AtomicInteger minimapRenders = new AtomicInteger();
        AtomicInteger gameRenders = new AtomicInteger();
        catalog.registerGlobalHud(
                "fpsmatch:minimap",
                HudRenderContext::minimapHudVisible,
                context -> minimapRenders.incrementAndGet()
        );
        catalog.registerGameTypeHook(
                "cs", context -> gameRenders.incrementAndGet()
        );

        catalog.renderRegistered(
                MAP,
                400,
                300,
                new HudRenderContext(
                        true, true, true, "cs", false, true
                ),
                id -> {
                }
        );

        assertEquals(0, minimapRenders.get());
        assertEquals(1, gameRenders.get());
    }

    @Test
    void twoPhaseSafeAreaCollectionHappensBeforeGlobalRender() {
        GlobalHudCatalog catalog = new GlobalHudCatalog();
        List<String> phases = new ArrayList<>();
        catalog.registerSafeAreaContributor("scoreboard", 100, (registry, ctx) -> {
            phases.add("collect:scoreboard");
            registry.contributeFixed("scoreboard", 100, new ScreenRect(0, 0, 100, 40));
        });
        catalog.registerGlobalHud(
                "fpsmatch:minimap",
                ctx -> true,
                ctx -> phases.add("render:minimap")
        );

        catalog.renderRegistered(
                MAP,
                400,
                300,
                new HudRenderContext(true, true, true, "cs", false),
                id -> phases.add("hook:" + id)
        );
        int collectIdx = phases.indexOf("collect:scoreboard");
        int renderIdx = phases.indexOf("render:minimap");
        assertTrue(collectIdx >= 0);
        assertTrue(renderIdx > collectIdx);
    }

    @Test
    void resolvedRendererReceivesTheSameFramePlacement() {
        GlobalHudCatalog catalog = new GlobalHudCatalog();
        AtomicReference<HudPlacement> renderedPlacement = new AtomicReference<>();
        catalog.registerSafeAreaContributor(
                "fpsmatch:minimap_hud",
                50,
                (registry, context) -> registry.contributeFlexible(
                        HudFlexibleRequest.minimapDefaults(HudAnchor.TOP_LEFT)
                )
        );
        catalog.registerResolvedGlobalHud(
                "fpsmatch:minimap",
                context -> true,
                (context, resolution) -> renderedPlacement.set(
                        resolution.placement(
                                HudFlexibleRequest.MINIMAP_HUD_ID
                        ).orElseThrow()
                )
        );

        var resolution = catalog.renderRegistered(
                MAP,
                400,
                300,
                new HudRenderContext(true, true, true, "cs", false),
                id -> {
                }
        );

        assertEquals(
                resolution.placement(HudFlexibleRequest.MINIMAP_HUD_ID)
                        .orElseThrow(),
                renderedPlacement.get()
        );
    }
}
