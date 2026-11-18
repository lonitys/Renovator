package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailContext;
import com.stilwerk.detail.DetailRule;
import com.stilwerk.detail.DetailTypes;
import com.stilwerk.detail.DetailTypes.Suggestion;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Doors in outer walls: lanterns on posts beside them, an awning above, or steps down in front. */
public final class EntranceRule implements DetailRule {
    @Override
    public String id() {
        return "entrance";
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        for (BlockPos p : Geo.built(v)) {
            BlockState s = v.state(p);
            if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            Direction facing = s.getValue(DoorBlock.FACING);
            Direction d = v.outside(p.relative(facing)) ? facing : v.outside(p.relative(facing.getOpposite())) ? facing.getOpposite() : null;
            if (d == null) continue;
            Direction side = d.getClockWise();
            BlockState slab = ctx.mats().woodSlab();
            if (slab.hasProperty(SlabBlock.TYPE)) slab = slab.setValue(SlabBlock.TYPE, SlabType.TOP);
            BlockState top = slab;
            Suggestion sg = DetailTypes.suggestion(id(), p, List.of(
                    ctx.variant(id(), "lanterns", e -> e.atomic(g -> {
                        // both sides or none: a single lantern post next to a door looks misplaced
                        for (Direction sd : new Direction[]{side, side.getOpposite()}) {
                            BlockPos post = p.relative(sd).relative(d);
                            g.put(post, ctx.mats().fence());
                            g.put(post.above(), Geo.hanging(Blocks.LANTERN.defaultBlockState(), false));
                        }
                    })),
                    ctx.variant(id(), "awning", e -> e.atomic(g -> {
                        BlockPos c = p.above(2).relative(d);
                        g.put(c, top);
                        g.put(c.relative(side), top);
                        g.put(c.relative(side.getOpposite()), top);
                    })),
                    ctx.variant(id(), "steps", e -> {
                        for (int k = 1; k <= 3; k++) {
                            BlockPos c = p.relative(d, k).below(k);
                            if (!v.free(c)) break;
                            e.put(c, Geo.stairs(ctx.mats().woodStairs(), d.getOpposite(), Half.BOTTOM));
                            if (!v.free(c.below())) break;
                        }
                    })));
            if (sg != null) out.add(sg);
        }
    }
}
