package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailContext;
import com.stilwerk.detail.DetailRule;
import com.stilwerk.detail.DetailTypes;
import com.stilwerk.detail.DetailTypes.Suggestion;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** Windows (glass in an outer wall): shutters, a stair frame, or a flower box below. */
public final class WindowRule implements DetailRule {
    @Override
    public String id() {
        return "window";
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        Map<BlockPos, Direction> glass = new HashMap<>();
        for (BlockPos p : Geo.built(v)) {
            if (!Geo.isGlass(v.state(p))) continue;
            Direction d = Geo.outsideFace(v, p);
            if (d != null) glass.put(p, d);
        }
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : Geo.built(v)) {
            Direction d = glass.get(start);
            if (d == null || seen.contains(start)) continue;
            Direction side = d.getClockWise();
            List<BlockPos> cluster = new ArrayList<>();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            q.add(start);
            seen.add(start);
            while (!q.isEmpty()) {
                BlockPos c = q.poll();
                cluster.add(c);
                for (Direction n : new Direction[]{Direction.UP, Direction.DOWN, side, side.getOpposite()}) {
                    BlockPos np = c.relative(n);
                    if (!seen.contains(np) && glass.get(np) == d) {
                        seen.add(np);
                        q.add(np);
                    }
                }
            }
            Direction.Axis axis = side.getAxis();
            Direction plus = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            int minY = cluster.stream().mapToInt(BlockPos::getY).min().orElseThrow();
            int maxY = cluster.stream().mapToInt(BlockPos::getY).max().orElseThrow();
            // per row the outermost cells; per column the lowest and highest cells
            Map<Integer, BlockPos[]> rows = new HashMap<>();
            Map<Integer, BlockPos[]> cols = new HashMap<>();
            for (BlockPos c : cluster) {
                rows.merge(c.getY(), new BlockPos[]{c, c}, (a, b) -> new BlockPos[]{
                        Geo.along(b[0], axis) < Geo.along(a[0], axis) ? b[0] : a[0], Geo.along(b[1], axis) > Geo.along(a[1], axis) ? b[1] : a[1]});
                cols.merge(Geo.along(c, axis), new BlockPos[]{c, c}, (a, b) -> new BlockPos[]{
                        b[0].getY() < a[0].getY() ? b[0] : a[0], b[1].getY() > a[1].getY() ? b[1] : a[1]});
            }
            BlockState leaves = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            Suggestion sg = DetailTypes.suggestion(id(), start, List.of(
                    ctx.variant(id(), "shutters", e -> {
                        for (BlockPos[] r : rows.values()) {
                            e.put(r[0].relative(plus.getOpposite()).relative(d), Geo.openTrapdoor(ctx.mats().trapdoor(), d.getOpposite()));
                            e.put(r[1].relative(plus).relative(d), Geo.openTrapdoor(ctx.mats().trapdoor(), d.getOpposite()));
                        }
                    }),
                    ctx.variant(id(), "frame", e -> {
                        for (BlockPos[] c : cols.values()) {
                            e.put(c[1].above().relative(d), Geo.stairs(ctx.mats().woodStairs(), d.getOpposite(), Half.TOP));
                            e.put(c[0].below().relative(d), Geo.stairs(ctx.mats().woodStairs(), d.getOpposite(), Half.BOTTOM));
                        }
                    }),
                    ctx.variant(id(), "flowers", e -> {
                        for (BlockPos[] c : cols.values()) {
                            BlockPos box = c[0].below().relative(d);
                            if (e.put(box, leaves)) e.put(box.relative(d), Geo.openTrapdoor(ctx.mats().trapdoor(), d.getOpposite()));
                        }
                    })));
            if (sg != null && maxY >= minY) out.add(sg);
        }
    }
}
