package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SamplerProfileTest {
    @Test
    void evaluatesBlockTagPropertyAndColorRulesInPriorityOrder() {
        SamplerProfile profile = new SamplerProfile(
                "tactical_default",
                42L,
                List.of(
                        BlockSampleRule.ignoreTag("minecraft:leaves", 10),
                        BlockSampleRule.color("minecraft:stone", 0xFF808080, 20),
                        BlockSampleRule.color("minecraft:grass_block", 0xFF228B22, 30),
                        BlockSampleRule.transparent("minecraft:air", 100)
                )
        );
        assertEquals(SampleDecision.ignore(), profile.sample("minecraft:oak_leaves", List.of("minecraft:leaves"), null));
        assertEquals(SampleDecision.color(0xFF808080), profile.sample("minecraft:stone", List.of(), null));
        assertEquals(SampleDecision.color(0xFF228B22), profile.sample("minecraft:grass_block", List.of(), null));
        assertEquals(SampleDecision.transparent(), profile.sample("minecraft:air", List.of(), null));
        assertEquals(SampleDecision.ignore(), profile.sample("minecraft:unknown", List.of(), null));
    }

    @Test
    void rejectsDuplicatePrioritiesAndEmptyProfiles() {
        assertThrows(IllegalArgumentException.class, () -> new SamplerProfile("x", 1L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SamplerProfile(
                "x", 1L,
                List.of(
                        BlockSampleRule.color("minecraft:stone", 0xFF000000, 1),
                        BlockSampleRule.color("minecraft:dirt", 0xFF111111, 1)
                )
        ));
    }
}
