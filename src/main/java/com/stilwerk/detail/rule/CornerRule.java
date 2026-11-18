package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailContext;
import com.stilwerk.detail.DetailRule;
import com.stilwerk.detail.DetailTypes;
import com.stilwerk.detail.DetailTypes.Suggestion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.WallTorchBlock;

/** Convex outer corners near the top of the wall: a lantern on a bracket, a soul lantern on a chain, or torches. */
public final class CornerRule implements DetailRule {
    @Override
    public String id() {
        return "corner";
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        // the highest corner block per (x, z, corner directions)
        Map<String, BlockPos> top = new HashMap<>();
        Map<String, Direction[]> dirs = new HashMap<>();
        Map<String, Integer> bottom = new HashMap<>();
        for (BlockPos p : Geo.built(v)) {
            for (int i = 0; i < 4; i++) {
                Direction a = Geo.HORIZONTAL[i], b = Geo.HORIZONTAL[(i + 1) % 4];
                if (!v.outside(p.relative(a)) || !v.outside(p.relative(b))) continue;
                String k = p.getX() + ":" + p.getZ() + ":" + i;
                top.merge(k, p, (x, y) -> y.getY() > x.getY() ? y : x);
                bottom.merge(k, p.getY(), Math::min);
                dirs.put(k, new Direction[]{a, b});
            }
        }
        top.keySet().stream().sorted().forEach(k -> {
            BlockPos t = top.get(k);
            if (t.getY() - bottom.get(k) < 2) return; // at least 3 high
            BlockPos p = t.below();
            Direction a = dirs.get(k)[0], b = dirs.get(k)[1];
            BlockPos arm = p.relative(a).relative(b);
            if (!v.outside(arm) || !v.outside(arm.below())) return;
            Suggestion sg = DetailTypes.suggestion(id(), p, List.of(
                    ctx.variant(id(), "lantern", e -> e.atomic(g -> {
                        g.put(arm, ctx.mats().fence());
                        g.put(arm.below(), Geo.hanging(Blocks.LANTERN.defaultBlockState(), true));
                    })),
                    ctx.variant(id(), "soul", e -> e.atomic(g -> {
                        g.put(arm, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                        g.put(arm.below(), Geo.hanging(Blocks.SOUL_LANTERN.defaultBlockState(), true));
                    })),
                    ctx.variant(id(), "torches", e -> e.atomic(g -> {
                        g.put(p.relative(a), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, a));
                        g.put(p.relative(b), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, b));
                    }))));
            if (sg != null) out.add(sg);
        });
    }
}
