package com.ptcrys.fpsmatch.core.minimap.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Migrates anonymous bomb areas into stable site_N ids without inferring A/B from names/order semantics beyond index.
 * Index is order-stable for a given input list only; administrators rebind display names separately.
 */
public final class BombSiteIdAssigner {
    private BombSiteIdAssigner() {
    }

    public static List<BombSiteDefinition> assignAnonymous(List<WorldAxisAlignedBounds> areas) {
        Objects.requireNonNull(areas, "areas");
        List<BombSiteDefinition> sites = new ArrayList<>(areas.size());
        for (int index = 0; index < areas.size(); index++) {
            String id = "site_" + (index + 1);
            sites.add(BombSiteDefinition.of(id, areas.get(index)));
        }
        return List.copyOf(sites);
    }

    public static String stableIdForIndex(int zeroBasedIndex) {
        if (zeroBasedIndex < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        return "site_" + (zeroBasedIndex + 1);
    }
}