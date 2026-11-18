package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailContext;
import com.stilwerk.detail.DetailRule;
import com.stilwerk.detail.DetailTypes;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.detail.Edit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Long flat outer walls (≥5 wide, ≥3 high): pillars at both ends and every 4 blocks. */
public final class PillarsRule implements DetailRule {
    @Override
    public String id() {
        return "pillars";
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        for (Walls.Plane pl : Walls.planes(v)) {
            for (List<Integer> seg : Walls.segments(pl, 3)) {
                if (seg.size() < 5) continue;
                List<Integer> cols = new ArrayList<>();
                for (int i = 0; i < seg.size(); i += 4) cols.add(seg.get(i));
                if (!cols.contains(seg.get(seg.size() - 1))) cols.add(seg.get(seg.size() - 1));
                List<BlockPos> cells = new ArrayList<>();
                for (int c : cols) {
                    for (BlockPos p : pl.columns().get(c)) if (Geo.isFull(v, p)) cells.add(p);
                }
                if (cells.size() < 3) continue;
                BlockPos anchor = pl.columns().get(seg.get(0)).get(0);
                Suggestion sg = DetailTypes.suggestion(id(), anchor, List.of(
                        ctx.variant(id(), "log", e -> fill(e, cells, ctx.mats().log())),
                        ctx.variant(id(), "stripped", e -> fill(e, cells, ctx.mats().strippedLog())),
                        ctx.variant(id(), "stone", e -> fill(e, cells, ctx.mats().stoneFull()))));
                if (sg != null) out.add(sg);
            }
        }
    }

    private static void fill(Edit e, List<BlockPos> cells, BlockState s) {
        for (BlockPos p : cells) e.replace(p, s);
    }
}
