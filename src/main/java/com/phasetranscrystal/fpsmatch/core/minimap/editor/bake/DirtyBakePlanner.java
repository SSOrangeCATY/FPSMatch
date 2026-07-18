package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DirtyBakePlanner {
    private final long debounceMillis;
    private final long maxDelayMillis;
    private final int maxJobs;
    private final Map<SectionCoord, DirtyEntry> dirty = new LinkedHashMap<>();

    public DirtyBakePlanner(Duration debounce, Duration maxDelay, int maxJobs) {
        Objects.requireNonNull(debounce, "debounce");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (debounce.isNegative() || maxDelay.isNegative() || maxJobs <= 0) {
            throw new IllegalArgumentException("Dirty bake planner settings are invalid");
        }
        this.debounceMillis = debounce.toMillis();
        this.maxDelayMillis = maxDelay.toMillis();
        this.maxJobs = maxJobs;
    }

    public void markSectionDirty(SectionCoord section, long sectionRevision, long nowMillis) {
        Objects.requireNonNull(section, "section");
        DirtyEntry existing = dirty.get(section);
        if (existing == null) {
            dirty.put(section, new DirtyEntry(sectionRevision, nowMillis, nowMillis));
            return;
        }
        dirty.put(section, new DirtyEntry(
                Math.max(existing.sectionRevision, sectionRevision),
                existing.firstMarkedAtMillis,
                nowMillis
        ));
    }

    public List<DirtyBakeJob> plan(long nowMillis) {
        List<DirtyBakeJob> jobs = new ArrayList<>();
        List<SectionCoord> consumed = new ArrayList<>();
        for (Map.Entry<SectionCoord, DirtyEntry> entry : dirty.entrySet()) {
            DirtyEntry value = entry.getValue();
            boolean debounceElapsed = nowMillis - value.lastMarkedAtMillis >= debounceMillis;
            boolean maxDelayElapsed = nowMillis - value.firstMarkedAtMillis >= maxDelayMillis;
            if (debounceElapsed || maxDelayElapsed) {
                jobs.add(new DirtyBakeJob(entry.getKey(), value.sectionRevision));
                consumed.add(entry.getKey());
                if (jobs.size() >= maxJobs) {
                    break;
                }
            }
        }
        for (SectionCoord section : consumed) {
            dirty.remove(section);
        }
        return List.copyOf(jobs);
    }

    private record DirtyEntry(long sectionRevision, long firstMarkedAtMillis, long lastMarkedAtMillis) {
    }
}
