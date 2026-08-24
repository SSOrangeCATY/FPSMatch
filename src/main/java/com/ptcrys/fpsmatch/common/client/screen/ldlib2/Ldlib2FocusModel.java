package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Pure ordered-focus state used by the LDLib2 screen adapter. */
public final class Ldlib2FocusModel<T> {
    private final Predicate<? super T> available;
    private List<T> targets = List.of();
    private T focused;

    public Ldlib2FocusModel(Predicate<? super T> available) {
        this.available = Objects.requireNonNull(available, "available");
    }

    public void setTargets(List<? extends T> targets) {
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }

    public List<T> targets() {
        return targets;
    }

    public Optional<T> focused() {
        return Optional.ofNullable(focused);
    }

    public void adopt(T target) {
        Objects.requireNonNull(target, "target");
        if (!targets.contains(target) || !available.test(target)) {
            throw new IllegalArgumentException("Target is not available in the current focus order");
        }
        focused = target;
    }

    public Optional<T> move(boolean reverse) {
        if (targets.isEmpty()) {
            return clear();
        }
        int current = focused == null ? -1 : targets.indexOf(focused);
        int start = current >= 0 ? current : reverse ? 0 : targets.size() - 1;
        int step = reverse ? -1 : 1;
        for (int offset = 1; offset <= targets.size(); offset++) {
            int index = Math.floorMod(start + step * offset, targets.size());
            T candidate = targets.get(index);
            if (available.test(candidate)) {
                focused = candidate;
                return Optional.of(candidate);
            }
        }
        return clear();
    }

    public Optional<T> reconcile() {
        int current = focused == null ? -1 : targets.indexOf(focused);
        if (current >= 0 && available.test(focused)) {
            return Optional.of(focused);
        }
        int start = current >= 0 ? current : targets.size() - 1;
        for (int offset = 1; offset <= targets.size(); offset++) {
            int index = Math.floorMod(start + offset, targets.size());
            T candidate = targets.get(index);
            if (available.test(candidate)) {
                focused = candidate;
                return Optional.of(candidate);
            }
        }
        return clear();
    }

    public Optional<T> clear() {
        focused = null;
        return Optional.empty();
    }
}
