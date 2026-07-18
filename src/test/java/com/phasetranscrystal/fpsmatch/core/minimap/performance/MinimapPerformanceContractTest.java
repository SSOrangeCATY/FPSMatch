package com.phasetranscrystal.fpsmatch.core.minimap.performance;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinimapPerformanceContractTest {
    @Test
    void freezesStandardAndStressFixtureScales() {
        assertEquals(
                new MinimapPerformanceContract.Fixture(
                        "standard", 4096, 8, 64, 512, 64
                ),
                MinimapPerformanceContract.STANDARD
        );
        assertEquals(
                new MinimapPerformanceContract.Fixture(
                        "stress", 8192, 16, 128, 4096, 256
                ),
                MinimapPerformanceContract.STRESS
        );
    }

    @Test
    void freezesFormalSamplingAndReleaseThresholds() {
        assertEquals(Duration.ofMinutes(2), MinimapPerformanceContract.FORMAL.warmup());
        assertEquals(Duration.ofMinutes(5), MinimapPerformanceContract.FORMAL.sample());
        assertEquals(3, MinimapPerformanceContract.FORMAL.runs());
        assertEquals(0.75, MinimapPerformanceContract.STANDARD_HUD_P95_MS);
        assertEquals(1.5, MinimapPerformanceContract.STANDARD_HUD_P99_MS);
        assertEquals(2.0, MinimapPerformanceContract.STANDARD_TACTICAL_P95_MS);
        assertEquals(500.0, MinimapPerformanceContract.TACTICAL_OPEN_MAX_MS);
        assertEquals(8.0, MinimapPerformanceContract.RENDER_TASK_MAX_MS);
        assertEquals(128L * 1024 * 1024, MinimapPerformanceContract.STANDARD_HEAP_MAX_BYTES);
        assertEquals(128L * 1024 * 1024, MinimapPerformanceContract.STANDARD_TEXTURE_MAX_BYTES);
        assertEquals(16L * 1024, MinimapPerformanceContract.MARKER_BYTES_PER_SECOND_MAX);
        assertEquals(0.5, MinimapPerformanceContract.STANDARD_SERVER_P95_MS);
        assertEquals(2.0, MinimapPerformanceContract.STANDARD_SERVER_P99_MS);
        assertEquals(3.0, MinimapPerformanceContract.STRESS_HUD_P99_MS);
        assertEquals(4.0, MinimapPerformanceContract.STRESS_SERVER_P99_MS);
    }

    @Test
    void percentileUsesNearestRankOverSortedNanoseconds() {
        long[] samples = {5_000_000L, 1_000_000L, 4_000_000L, 2_000_000L, 3_000_000L};

        assertEquals(4.0, MinimapPerformanceContract.percentileMillis(samples, 0.80));
        assertEquals(5.0, MinimapPerformanceContract.percentileMillis(samples, 0.99));
    }

    @Test
    void freezesRealTimeCadenceAndThirtyTwoPlayerServerLoad() {
        assertEquals(60, MinimapPerformanceContract.CLIENT_FRAME_HZ);
        assertEquals(20, MinimapPerformanceContract.SERVER_TICK_HZ);
        assertEquals(5, MinimapPerformanceContract.MARKER_UPDATE_HZ);
        assertEquals(32, MinimapPerformanceContract.SERVER_RECEIVERS);
        assertEquals(
                300,
                MinimapPerformanceContract.expectedSamples(
                        Duration.ofSeconds(5),
                        MinimapPerformanceContract.CLIENT_FRAME_HZ
                )
        );
        assertEquals(
                100,
                MinimapPerformanceContract.expectedSamples(
                        Duration.ofSeconds(5),
                        MinimapPerformanceContract.SERVER_TICK_HZ
                )
        );
        assertEquals(
                25,
                MinimapPerformanceContract.expectedSamples(
                        Duration.ofSeconds(5),
                        MinimapPerformanceContract.MARKER_UPDATE_HZ
                )
        );
    }

    @Test
    void heapAndResidentTextureMetricsUseDeltasAndActualUniqueAllocations() {
        assertEquals(80L, MinimapPerformanceContract.heapDeltaBytes(120L, 200L));
        assertEquals(0L, MinimapPerformanceContract.heapDeltaBytes(200L, 180L));
        assertEquals(
                176L,
                MinimapPerformanceContract.residentTextureBytes(List.of(
                        new MinimapPerformanceContract.ResidentTexture("a", 4, 4),
                        new MinimapPerformanceContract.ResidentTexture("a", 4, 4),
                        new MinimapPerformanceContract.ResidentTexture("b", 8, 2),
                        new MinimapPerformanceContract.ResidentTexture("c", 2, 6)
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MinimapPerformanceContract.residentTextureBytes(List.of(
                        new MinimapPerformanceContract.ResidentTexture("a", 4, 4),
                        new MinimapPerformanceContract.ResidentTexture("a", 8, 8)
                ))
        );
    }

    @Test
    void cpuOverheadSubtractsTheCapabilityDisabledBaselineWithoutGoingNegative() {
        assertEquals(0.35, MinimapPerformanceContract.cpuOverheadMillis(0.5, 0.15));
        assertEquals(0.0, MinimapPerformanceContract.cpuOverheadMillis(0.1, 0.2));
        assertEquals(350_000L, MinimapPerformanceContract.cpuOverheadNanos(
                500_000L, 150_000L
        ));
        assertEquals(0L, MinimapPerformanceContract.cpuOverheadNanos(
                100_000L, 200_000L
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> MinimapPerformanceContract.cpuOverheadMillis(Double.NaN, 0.1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MinimapPerformanceContract.cpuOverheadMillis(0.1, -0.1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MinimapPerformanceContract.cpuOverheadNanos(-1L, 0L)
        );
    }
}
