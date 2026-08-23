package com.ptcrys.fpsmatch.core.minimap.performance;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MinimapPerformanceContract {
    public static final Fixture STANDARD = new Fixture(
            "standard", 4096, 8, 64, 512, 64
    );
    public static final Fixture STRESS = new Fixture(
            "stress", 8192, 16, 128, 4096, 256
    );
    public static final Sampling FORMAL = new Sampling(
            Duration.ofMinutes(2), Duration.ofMinutes(5), 3
    );

    public static final int CLIENT_FRAME_HZ = 60;
    public static final int SERVER_TICK_HZ = 20;
    public static final int MARKER_UPDATE_HZ = 5;
    public static final int SERVER_RECEIVERS = 32;

    public static final double STANDARD_HUD_P95_MS = 0.75;
    public static final double STANDARD_HUD_P99_MS = 1.5;
    public static final double STANDARD_TACTICAL_P95_MS = 2.0;
    public static final double TACTICAL_OPEN_MAX_MS = 500.0;
    public static final double RENDER_TASK_MAX_MS = 8.0;
    public static final long STANDARD_HEAP_MAX_BYTES = 128L * 1024 * 1024;
    public static final long STANDARD_TEXTURE_MAX_BYTES = 128L * 1024 * 1024;
    public static final long MARKER_BYTES_PER_SECOND_MAX = 16L * 1024;
    public static final double STANDARD_SERVER_P95_MS = 0.5;
    public static final double STANDARD_SERVER_P99_MS = 2.0;
    public static final double STRESS_HUD_P99_MS = 3.0;
    public static final double STRESS_SERVER_P99_MS = 4.0;

    private MinimapPerformanceContract() {
    }

    public static double percentileMillis(long[] samplesNanos, double percentile) {
        Objects.requireNonNull(samplesNanos, "samplesNanos");
        if (samplesNanos.length == 0 || !(percentile > 0.0) || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile samples and rank must be non-empty");
        }
        long[] sorted = samplesNanos.clone();
        Arrays.sort(sorted);
        int rank = Math.max(1, (int) Math.ceil(percentile * sorted.length));
        return sorted[rank - 1] / 1_000_000.0;
    }

    public static int expectedSamples(Duration duration, int frequencyHz) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero() || frequencyHz <= 0) {
            throw new IllegalArgumentException("Sampling duration and frequency must be positive");
        }
        long scaledNanos = Math.multiplyExact(duration.toNanos(), frequencyHz);
        long samples = scaledNanos / Duration.ofSeconds(1).toNanos();
        if (samples <= 0 || samples > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sampling count is outside the supported range");
        }
        return (int) samples;
    }

    public static long heapDeltaBytes(long baselineBytes, long peakBytes) {
        if (baselineBytes < 0 || peakBytes < 0) {
            throw new IllegalArgumentException("Heap measurements must be non-negative");
        }
        return Math.max(0L, peakBytes - baselineBytes);
    }

    public static double cpuOverheadMillis(
            double enabledMillis,
            double baselineMillis
    ) {
        if (!Double.isFinite(enabledMillis) || !Double.isFinite(baselineMillis)
                || enabledMillis < 0.0 || baselineMillis < 0.0) {
            throw new IllegalArgumentException("CPU measurements must be finite and non-negative");
        }
        return Math.max(0.0, enabledMillis - baselineMillis);
    }

    public static long cpuOverheadNanos(long enabledNanos, long baselineNanos) {
        if (enabledNanos < 0L || baselineNanos < 0L) {
            throw new IllegalArgumentException("CPU measurements must be non-negative");
        }
        return Math.max(0L, enabledNanos - baselineNanos);
    }

    public static long residentTextureBytes(List<ResidentTexture> textures) {
        Objects.requireNonNull(textures, "textures");
        Map<String, ResidentTexture> unique = new LinkedHashMap<>();
        for (ResidentTexture texture : textures) {
            ResidentTexture previous = unique.putIfAbsent(
                    texture.textureKey(), texture
            );
            if (previous != null
                    && (previous.width() != texture.width()
                    || previous.height() != texture.height())) {
                throw new IllegalArgumentException(
                        "Resident texture dimensions conflict for " + texture.textureKey()
                );
            }
        }
        long bytes = 0L;
        for (ResidentTexture texture : unique.values()) {
            long pixels = Math.multiplyExact(
                    (long) texture.width(), texture.height()
            );
            bytes = Math.addExact(bytes, Math.multiplyExact(pixels, 4L));
        }
        return bytes;
    }

    public record Fixture(
            String id,
            int canvasEdge,
            int floors,
            int sourceLayers,
            int regions,
            int markers
    ) {
        public Fixture {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()
                    || canvasEdge <= 0
                    || floors <= 0
                    || sourceLayers <= 0
                    || regions <= 0
                    || markers <= 0) {
                throw new IllegalArgumentException("Performance fixture values must be positive");
            }
        }
    }

    public record Sampling(Duration warmup, Duration sample, int runs) {
        public Sampling {
            Objects.requireNonNull(warmup, "warmup");
            Objects.requireNonNull(sample, "sample");
            if (warmup.isNegative() || warmup.isZero()
                    || sample.isNegative() || sample.isZero()
                    || runs <= 0) {
                throw new IllegalArgumentException("Performance sampling must be positive");
            }
        }
    }

    public record ResidentTexture(String textureKey, int width, int height) {
        public ResidentTexture {
            Objects.requireNonNull(textureKey, "textureKey");
            if (textureKey.isBlank() || width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "Resident texture key and dimensions must be valid"
                );
            }
        }
    }
}
