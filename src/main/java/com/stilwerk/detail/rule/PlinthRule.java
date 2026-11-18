package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailContext;
import com.stilwerk.detail.DetailRule;
import com.stilwerk.detail.DetailTypes;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.mix.Hash;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** The lowest row of the outer walls: a different material, a stair skirt, or a mossy mix. */
public final class PlinthRule implements DetailRule {
    @Override
    public String id() {
        return "plinth";
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        Map<BlockPos, Direction> cells = new LinkedHashMap<>();
        for (BlockPos p : Geo.built(v)) {
            if (!Geo.isWallBody(v, p) || v.built(p.below())) continue;
            for (Direction d : Geo.HORIZONTAL) {
                if (v.outside(p.relative(d))) {
                    cells.putIfAbsent(p, d);
                }
            }
        }
        if (cells.size() < 4) return;
        List<BlockPos> list = new ArrayList<>(cells.keySet());
        Suggestion sg = DetailTypes.suggestion(id(), list.get(0), List.of(
                ctx.variant(id(), "material", e -> {
                    for (BlockPos p : list) {
                        FamilyIndex.Member m = v.member(p);
                        boolean wood = m != null && m.family().wood();
                        BlockState cur = v.state(p);
                        BlockState to = wood ? ctx.mats().stoneFull()
                                : cur.is(Blocks.COBBLESTONE) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                        e.replace(p, to);
                    }
                }),
                ctx.variant(id(), "skirt", e -> {
                    for (Map.Entry<BlockPos, Direction> c : cells.entrySet()) {
                        BlockPos front = c.getKey().relative(c.getValue());
                        if (!v.free(front.below())) e.put(front, Geo.stairs(ctx.mats().stoneStairs(), c.getValue().getOpposite(), Half.BOTTOM));
                    }
                }),
                ctx.variant(id(), "mossy", e -> {
                    for (BlockPos p : list) {
                        double u = Hash.unit(ctx.seed(), p, 21);
                        BlockState to = u < 0.4 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                                : u < 0.8 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                        e.replace(p, to);
                    }
                })));
        if (sg != null) out.add(sg);
    }
}
