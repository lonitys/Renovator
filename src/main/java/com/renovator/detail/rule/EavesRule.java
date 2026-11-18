package com.renovator.detail.rule;

import com.renovator.detail.BuildingView;
import com.renovator.detail.DetailContext;
import com.renovator.detail.DetailRule;
import com.renovator.detail.DetailTypes;
import com.renovator.detail.DetailTypes.Suggestion;
import com.renovator.family.FamilyIndex;
import com.renovator.family.Shape;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Roof edges (stairs with outside air in front of their low side and below): eave trim, fascia or overhang. */
public final class EavesRule implements DetailRule {
    @Override
    public String id() {
        return "eaves";
    }

    private static boolean eave(BuildingView v, BlockPos p) {
        BlockState s = v.state(p);
        if (!v.built(p) || !Geo.isBottomStair(s)) return false;
        Direction low = s.getValue(StairBlock.FACING).getOpposite();
        return v.outside(p.relative(low)) && v.outside(p.below());
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : Geo.built(v)) {
            if (seen.contains(p) || !eave(v, p)) continue;
            Direction f = v.state(p).getValue(StairBlock.FACING);
            // one suggestion per edge line: same height and direction, neighbours along the edge
            List<BlockPos> line = new ArrayList<>();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            q.add(p);
            seen.add(p);
            while (!q.isEmpty()) {
                BlockPos c = q.poll();
                line.add(c);
                for (Direction d : new Direction[]{f.getClockWise(), f.getCounterClockWise()}) {
                    BlockPos n = c.relative(d);
                    if (!seen.contains(n) && eave(v, n) && v.state(n).getValue(StairBlock.FACING) == f) {
                        seen.add(n);
                        q.add(n);
                    }
                }
            }
            if (line.size() < 2) continue;
            Direction low = f.getOpposite();
            Suggestion sg = DetailTypes.suggestion(id(), line.get(0), List.of(
                    ctx.variant(id(), "under", e -> {
                        for (BlockPos c : line) e.put(c.below(), Geo.stairs(v.state(c), f, Half.TOP));
                    }),
                    ctx.variant(id(), "fascia", e -> {
                        for (BlockPos c : line) e.put(c.relative(low), Geo.openTrapdoor(ctx.mats().trapdoor(), f));
                    }),
                    ctx.variant(id(), "overhang", e -> {
                        for (BlockPos c : line) e.put(c.relative(low), slabFor(v.state(c), ctx));
                    })));
            if (sg != null) out.add(sg);
        }
    }

    private static BlockState slabFor(BlockState stair, DetailContext ctx) {
        FamilyIndex.Member m = FamilyIndex.get().lookup(stair.getBlock());
        Block slab = m == null ? null : m.family().get(Shape.SLAB);
        BlockState s = slab != null ? slab.defaultBlockState() : ctx.mats().woodSlab();
        return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : s;
    }
}
