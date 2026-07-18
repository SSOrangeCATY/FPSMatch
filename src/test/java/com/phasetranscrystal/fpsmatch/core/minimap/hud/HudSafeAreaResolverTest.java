package com.phasetranscrystal.fpsmatch.core.minimap.hud;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudSafeAreaResolverTest {
    private static final MapKey MAP = new MapKey("cs", "dust2");

    @Test
    void placesByPriorityDescendingThenIdAscendingWithoutRegistrationOrder() {
        HudSafeAreaRegistry registry = new HudSafeAreaRegistry();
        registry.beginFrame(MAP, 400, 300);
        // register low priority first, high later - order must not matter
        registry.contributeFlexible(flexible("minimap", 50, HudAnchor.TOP_LEFT));
        registry.contributeFixed("scoreboard", 100, new ScreenRect(0, 0, 120, 40));
        registry.contributeFixed("aaa-low", 10, new ScreenRect(300, 0, 80, 20));
        HudSafeAreaResolution resolution = registry.resolve();

        assertEquals(List.of("scoreboard", "minimap", "aaa-low"), resolution.placementOrder());
        assertTrue(resolution.placement("scoreboard").isPresent());
        assertTrue(resolution.placement("minimap").isPresent());
        assertTrue(resolution.placement("aaa-low").isPresent());
    }

    @Test
    void configuredAnchorFirstThenDefaultOrderWithPreferredThenShrinkStepEight() {
        HudSafeAreaRegistry registry = new HudSafeAreaRegistry();
        registry.beginFrame(MAP, 400, 300);
        // Strip only collides with preferred 128 at TOP_LEFT; allows shrink to <=104 with step 8.
        registry.contributeFixed("killfeed", 100, new ScreenRect(8 + 110, 8, 30, 30));
        registry.contributeFlexible(new HudFlexibleRequest(
                "minimap",
                50,
                128,
                96,
                8,
                HudAnchor.TOP_LEFT
        ));
        HudSafeAreaResolution resolution = registry.resolve();
        HudPlacement placement = resolution.placement("minimap").orElseThrow();
        assertFalse(placement.hidden());
        assertEquals(HudAnchor.TOP_LEFT, placement.anchor().orElseThrow());
        assertTrue(placement.size() < 128);
        assertTrue(placement.size() >= 96);
        assertEquals(0, (128 - placement.size()) % 8);
    }

    @Test
    void fallsBackToNextAnchorWhenPreferredCornerFullyBlocked() {
        HudSafeAreaRegistry registry = new HudSafeAreaRegistry();
        registry.beginFrame(MAP, 400, 300);
        // Fully occupy TOP_RIGHT corner region so all sizes fail there.
        registry.contributeFixed("killfeed", 100, new ScreenRect(400 - 8 - 140, 0, 148, 160));
        registry.contributeFlexible(flexible("minimap", 50, HudAnchor.TOP_RIGHT));
        HudPlacement placement = registry.resolve().placement("minimap").orElseThrow();
        assertFalse(placement.hidden());
        assertEquals(HudAnchor.TOP_LEFT, placement.anchor().orElseThrow());
        assertEquals(128, placement.size());
    }

    @Test
    void hidesWithDeterministicDiagnosticsDedupedByMapKeyAndConflictSet() {
        HudSafeAreaRegistry registry = new HudSafeAreaRegistry();
        registry.beginFrame(MAP, 160, 160);
        // fully occupy all four corners for size >= 96
        registry.contributeFixed("tl", 100, new ScreenRect(0, 0, 80, 80));
        registry.contributeFixed("tr", 100, new ScreenRect(80, 0, 80, 80));
        registry.contributeFixed("bl", 100, new ScreenRect(0, 80, 80, 80));
        registry.contributeFixed("br", 100, new ScreenRect(80, 80, 80, 80));
        registry.contributeFlexible(flexible("minimap", 50, HudAnchor.TOP_LEFT));
        HudSafeAreaResolution first = registry.resolve();
        assertTrue(first.placement("minimap").orElseThrow().hidden());
        assertEquals(Set.of("bl", "br", "tl", "tr"), first.placement("minimap").orElseThrow().conflictIds());

        registry.beginFrame(MAP, 160, 160);
        registry.contributeFixed("tl", 100, new ScreenRect(0, 0, 80, 80));
        registry.contributeFixed("tr", 100, new ScreenRect(80, 0, 80, 80));
        registry.contributeFixed("bl", 100, new ScreenRect(0, 80, 80, 80));
        registry.contributeFixed("br", 100, new ScreenRect(80, 80, 80, 80));
        registry.contributeFlexible(flexible("minimap", 50, HudAnchor.TOP_LEFT));
        HudSafeAreaResolution second = registry.resolve();
        // diagnostic dedupe: same map + conflict set yields one diagnostic entry across frames in registry
        assertEquals(1, registry.diagnostics().size());
        assertEquals(first.placement("minimap").orElseThrow().conflictIds(), second.placement("minimap").orElseThrow().conflictIds());
    }

    @Test
    void occupiedRectsInflateByFourPixelsAndConfigPreviewReusesResolver() {
        ScreenRect placed = new ScreenRect(10, 10, 20, 20);
        ScreenRect inflated = placed.inflated(4);
        assertEquals(new ScreenRect(6, 6, 28, 28), inflated);

        HudSafeAreaResolver resolver = new HudSafeAreaResolver();
        List<HudSafeAreaEntry> entries = List.of(
                HudSafeAreaEntry.fixed("vote", 100, new ScreenRect(0, 0, 100, 30)),
                HudSafeAreaEntry.flexible(flexible("minimap", 50, HudAnchor.TOP_LEFT))
        );
        HudSafeAreaResolution live = resolver.resolve(MAP, 320, 240, entries);
        HudSafeAreaResolution preview = resolver.resolve(MAP, 320, 240, entries);
        assertEquals(live.placement("minimap"), preview.placement("minimap"));
        assertEquals(live.placementOrder(), preview.placementOrder());
    }

    @Test
    void dynamicContributorChangesAffectNextFrameOnly() {
        HudSafeAreaRegistry registry = new HudSafeAreaRegistry();
        registry.beginFrame(MAP, 400, 300);
        registry.contributeFlexible(flexible("minimap", 50, HudAnchor.TOP_RIGHT));
        HudPlacement free = registry.resolve().placement("minimap").orElseThrow();
        assertFalse(free.hidden());
        assertEquals(HudAnchor.TOP_RIGHT, free.anchor().orElseThrow());

        registry.beginFrame(MAP, 400, 300);
        registry.contributeFixed("killfeed", 100, new ScreenRect(400 - 8 - 140, 0, 148, 160));
        registry.contributeFlexible(flexible("minimap", 50, HudAnchor.TOP_RIGHT));
        HudPlacement after = registry.resolve().placement("minimap").orElseThrow();
        assertFalse(after.hidden());
        assertEquals(HudAnchor.TOP_LEFT, after.anchor().orElseThrow());
        assertTrue(after.size() >= 96);
    }

    @Test
    void minimapDefaultsArePriorityFiftyPreferredOneTwentyEightMinNinetySix() {
        HudFlexibleRequest defaults = HudFlexibleRequest.minimapDefaults(HudAnchor.TOP_LEFT);
        assertEquals(50, defaults.priority());
        assertEquals(128, defaults.preferredSize());
        assertEquals(96, defaults.minSize());
        assertEquals("fpsmatch:minimap_hud", defaults.id());
    }

    private static HudFlexibleRequest flexible(String id, int priority, HudAnchor anchor) {
        return new HudFlexibleRequest(id, priority, 128, 96, 8, anchor);
    }
}