package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Outer wall faces grouped into planes and columns (used by pillars and plain). */
final class Walls {
    private Walls() {}

    /** A wall plane: all built cells with outside air in direction {@code out}, at one coordinate along out's axis. */
    record Plane(Direction out, int depth, TreeMap<Integer, List<BlockPos>> columns) {
        /** Coordinate along the wall (perpendicular horizontal axis). */
        Direction.Axis alongAxis() {
            return out.getClockWise().getAxis();
        }
    }

    static List<Plane> planes(BuildingView v) {
        Map<String, Plane> planes = new TreeMap<>();
        for (BlockPos p : Geo.built(v)) {
            for (Direction d : Geo.HORIZONTAL) {
                if (!v.outside(p.relative(d))) continue;
                int depth = Geo.along(p, d.getAxis());
                Plane pl = planes.computeIfAbsent(d + ":" + depth, k -> new Plane(d, depth, new TreeMap<>()));
                pl.columns().computeIfAbsent(Geo.along(p, pl.alongAxis()), k -> new ArrayList<>()).add(p);
            }
        }
        return new ArrayList<>(planes.values());
    }

    /** Contiguous runs of column coordinates whose column has at least {@code minHeight} cells. */
    static List<List<Integer>> segments(Plane pl, int minHeight) {
        List<List<Integer>> out = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        Integer last = null;
        for (Map.Entry<Integer, List<BlockPos>> e : pl.columns().entrySet()) {
            boolean ok = e.getValue().size() >= minHeight;
            if (ok && last != null && e.getKey() == last + 1) {
                cur.add(e.getKey());
            } else {
                if (cur.size() > 0) out.add(cur);
                cur = new ArrayList<>();
                if (ok) cur.add(e.getKey());
            }
            last = ok ? e.getKey() : null;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
}
