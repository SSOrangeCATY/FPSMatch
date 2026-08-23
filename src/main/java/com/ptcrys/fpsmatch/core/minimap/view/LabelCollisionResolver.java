package com.ptcrys.fpsmatch.core.minimap.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class LabelCollisionResolver {
    private LabelCollisionResolver() {
    }

    public static List<LabelCandidate> resolve(List<LabelCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<LabelCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt(LabelCandidate::priority)
                .reversed()
                .thenComparing(LabelCandidate::id));
        List<LabelCandidate> kept = new ArrayList<>();
        for (LabelCandidate candidate : ordered) {
            boolean collides = false;
            for (LabelCandidate existing : kept) {
                if (intersects(candidate, existing)) {
                    collides = true;
                    break;
                }
            }
            if (!collides) {
                kept.add(candidate);
            }
        }
        return List.copyOf(kept);
    }

    private static boolean intersects(LabelCandidate a, LabelCandidate b) {
        return a.x() < b.x() + b.width()
                && a.x() + a.width() > b.x()
                && a.y() < b.y() + b.height()
                && a.y() + a.height() > b.y();
    }
}
