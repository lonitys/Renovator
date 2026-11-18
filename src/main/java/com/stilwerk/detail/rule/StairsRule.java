package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailContext;
import com.stilwerk.detail.DetailRule;
import com.stilwerk.detail.DetailTypes;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.detail.Edit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Staircases (runs of ≥2 ascending stairs, at most 3 wide): railings on the open sides with a lantern at the bottom. */
public final class StairsRule implements DetailRule {
    @Override
    public String id() {
        return "stairs";
    }

    private static boolean step(BuildingView v, BlockPos p, Direction facing) {
        BlockState s = v.state(p);
        return v.built(p) && Geo.isBottomStair(s) && s.getValue(StairBlock.FACING) == facing;
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        Set<BlockPos> used = new HashSet<>();
        for (BlockPos p : Geo.built(v)) {
            BlockState s = v.state(p);
            if (!Geo.isBottomStair(s) || used.contains(p)) continue;
            Direction f = s.getValue(StairBlock.FACING);
            if (step(v, p.relative(f.getOpposite()).below(), f)) continue; // not the lowest step
            List<BlockPos> run = new ArrayList<>();
            for (BlockPos c = p; step(v, c, f); c = c.relative(f).above()) run.add(c);
            if (run.size() < 2) continue;
            used.addAll(run);
            Direction left = f.getCounterClockWise(), right = f.getClockWise();
            int width = 1;
            for (Direction side : new Direction[]{left, right}) {
                for (BlockPos c = p.relative(side); step(v, c, f) && width <= 4; c = c.relative(side)) width++;
            }
            if (width > 3) continue; // a roof, not a staircase
            boolean headroom = run.stream().allMatch(c -> v.free(c.above()) && v.free(c.above(2)));
            if (!headroom) continue;
            List<Direction> open = new ArrayList<>();
            for (Direction side : new Direction[]{left, right}) {
                if (run.stream().allMatch(c -> v.free(c.relative(side)))) open.add(side);
            }
            if (open.isEmpty()) continue;
            Suggestion sg = DetailTypes.suggestion(id(), p, List.of(
                    ctx.variant(id(), "fence", e -> rail(e, run, open, ctx.mats().fence(), true)),
                    ctx.variant(id(), "wall", e -> rail(e, run, open, ctx.mats().stoneWall(), true)),
                    ctx.variant(id(), "trapdoor", e -> {
                        for (Direction side : open)
                            for (BlockPos c : run) e.put(c.relative(side), Geo.openTrapdoor(ctx.mats().trapdoor(), side.getOpposite()));
                    })));
            if (sg != null) out.add(sg);
        }
    }

    private static void rail(Edit e, List<BlockPos> run, List<Direction> open, BlockState post, boolean lantern) {
        for (Direction side : open) {
            for (BlockPos c : run) e.put(c.relative(side), post);
            if (lantern) e.put(run.get(0).relative(side).above(), Geo.hanging(Blocks.LANTERN.defaultBlockState(), false));
        }
    }
}
