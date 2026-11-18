package com.renovator.detail.rule;

import com.renovator.detail.BuildingView;
import com.renovator.detail.DetailContext;
import com.renovator.detail.DetailRule;
import com.renovator.detail.DetailTypes;
import com.renovator.detail.DetailTypes.Suggestion;
import com.renovator.detail.Edit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Long flat outer walls (≥5 wide, ≥3 high): pillars at both ends and about every 4 blocks. A pillar column must be
 * all wall (no door, window or glass in it; shifted by up to 2 columns otherwise), columns that already are logs are
 * left alone, and only the wall's own material is replaced (the plinth below stays).
 */
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
                // the wall's own material: the most common wall block of the segment
                Map<Block, Integer> counts = new HashMap<>();
                for (int c : seg) for (BlockPos p : pl.columns().get(c)) if (Geo.isWallBody(v, p)) counts.merge(v.state(p).getBlock(), 1, Integer::sum);
                Block main = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                if (main == null) continue;
                Set<Integer> chosen = new LinkedHashSet<>();
                List<Integer> wanted = new ArrayList<>();
                for (int i = 0; i < seg.size(); i += 4) wanted.add(seg.get(i));
                if (!wanted.contains(seg.get(seg.size() - 1))) wanted.add(seg.get(seg.size() - 1));
                for (int c : wanted) {
                    for (int off : new int[]{0, 1, -1, 2, -2}) {
                        int cc = c + off;
                        if (!seg.contains(cc) || chosen.contains(cc)) continue;
                        if (columnOk(v, pl.columns().get(cc), main)) {
                            chosen.add(cc);
                            break;
                        }
                    }
                }
                List<BlockPos> cells = new ArrayList<>();
                for (int c : chosen) for (BlockPos p : pl.columns().get(c)) if (v.state(p).is(main)) cells.add(p);
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

    /** All cells are wall blocks (or an existing plinth), at least two of the main material, none a log already. */
    private static boolean columnOk(BuildingView v, List<BlockPos> column, Block main) {
        int mainCells = 0;
        for (BlockPos p : column) {
            if (Geo.isPillarBlock(v, p)) return false;
            if (!Geo.isWallBody(v, p)) return false;
            if (v.state(p).is(main)) mainCells++;
        }
        return mainCells >= 2;
    }

    private static void fill(Edit e, List<BlockPos> cells, BlockState s) {
        for (BlockPos p : cells) e.replace(p, s);
    }
}
