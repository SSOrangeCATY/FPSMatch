package com.ptcrys.fpsmatch.core.minimap.editor.raster;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class FloodFill {
    private final ColorTolerance tolerance;
    private final FillBudget budget;

    public FloodFill(ColorTolerance tolerance, FillBudget budget) {
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public int fill(RasterSurface surface, int startX, int startY, int fillRgba) {
        Objects.requireNonNull(surface, "surface");
        if (!surface.isSelected(startX, startY)) {
            return 0;
        }
        boolean startInherited = surface.isInherited(startX, startY);
        int target = startInherited ? 0 : surface.getPixel(startX, startY);
        if (!startInherited && target == fillRgba) {
            return 0;
        }

        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> accepted = new HashSet<>();
        queue.add(pack(startX, startY));
        visited.add(pack(startX, startY));

        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = unpackX(packed);
            int y = unpackY(packed);
            if (!surface.isSelected(x, y)) {
                continue;
            }
            boolean inherited = surface.isInherited(x, y);
            if (startInherited) {
                if (!inherited) {
                    continue;
                }
            } else {
                if (inherited || !tolerance.matches(target, surface.getPixel(x, y))) {
                    continue;
                }
            }
            accepted.add(packed);
            if (accepted.size() > budget.maxCells()) {
                throw new FillBudgetExceededException(budget.maxCells());
            }
            offer(queue, visited, surface, x + 1, y);
            offer(queue, visited, surface, x - 1, y);
            offer(queue, visited, surface, x, y + 1);
            offer(queue, visited, surface, x, y - 1);
        }

        for (long packed : accepted) {
            surface.setPixel(unpackX(packed), unpackY(packed), fillRgba);
        }
        return accepted.size();
    }

    private static void offer(
            ArrayDeque<Long> queue,
            Set<Long> visited,
            RasterSurface surface,
            int x,
            int y
    ) {
        if (x < 0 || y < 0 || x >= surface.width() || y >= surface.height()) {
            return;
        }
        long packed = pack(x, y);
        if (visited.add(packed)) {
            queue.addLast(packed);
        }
    }

    private static long pack(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackY(long packed) {
        return (int) packed;
    }
}
