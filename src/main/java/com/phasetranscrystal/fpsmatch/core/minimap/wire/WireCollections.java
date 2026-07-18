package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

final class WireCollections {
    private WireCollections() {
    }

    static <T> List<T> copyBounded(
            List<T> values,
            int maximumCount,
            String label
    ) {
        return copyBounded(
                values, maximumCount, Long.MAX_VALUE, ignored -> 0L, null, label
        );
    }

    static <T> List<T> copyBounded(
            List<T> values,
            int maximumCount,
            long maximumAggregate,
            ToLongFunction<T> aggregateBytes,
            String label
    ) {
        return copyBounded(
                values,
                maximumCount,
                maximumAggregate,
                aggregateBytes,
                null,
                label
        );
    }

    static <T, K> List<T> copyBoundedUnique(
            List<T> values,
            int maximumCount,
            Function<T, K> identity,
            String label
    ) {
        return copyBounded(
                values, maximumCount, Long.MAX_VALUE, ignored -> 0L,
                Objects.requireNonNull(identity, "identity"), label
        );
    }

    static <T, K> List<T> copyBoundedUnique(
            List<T> values,
            int maximumCount,
            long maximumAggregate,
            ToLongFunction<T> aggregateBytes,
            Function<T, K> identity,
            String label
    ) {
        return copyBounded(
                values,
                maximumCount,
                maximumAggregate,
                aggregateBytes,
                Objects.requireNonNull(identity, "identity"),
                label
        );
    }

    private static <T> List<T> copyBounded(
            List<T> values,
            int maximumCount,
            long maximumAggregate,
            ToLongFunction<T> aggregateBytes,
            Function<T, ?> identity,
            String label
    ) {
        Objects.requireNonNull(values, label);
        Objects.requireNonNull(aggregateBytes, "aggregateBytes");
        if (maximumCount < 0 || maximumAggregate < 0) {
            throw new IllegalArgumentException("wire collection limit is negative");
        }

        List<T> copy = new ArrayList<>();
        Set<Object> identities = identity == null ? null : new HashSet<>();
        long aggregate = 0;
        for (T value : values) {
            if (copy.size() >= maximumCount) {
                throw new IllegalArgumentException(label + " exceeds its count limit");
            }
            T checked = Objects.requireNonNull(value, label + " contains null");
            if (identities != null
                    && !identities.add(Objects.requireNonNull(
                    identity.apply(checked), label + " contains a null identity"
            ))) {
                throw new IllegalArgumentException(label + " contains a duplicate identity");
            }
            long additional = aggregateBytes.applyAsLong(checked);
            if (additional < 0 || aggregate > maximumAggregate - additional) {
                throw new IllegalArgumentException(label + " exceeds its aggregate limit");
            }
            aggregate += additional;
            copy.add(checked);
        }
        return List.copyOf(copy);
    }
}
