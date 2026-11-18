package com.renovator.detail.rule;

import com.renovator.detail.BuildingView;
import com.renovator.detail.DetailContext;
import com.renovator.detail.DetailRule;
import com.renovator.detail.DetailTypes;
import com.renovator.detail.DetailTypes.Suggestion;
import com.renovator.detail.DetailTypes.Variant;
import com.renovator.family.Family;
import com.renovator.family.FamilyIndex;
import com.renovator.family.Shape;
import com.renovator.family.StateTransfer;
import com.renovator.mix.MixPalette;
import com.renovator.mix.Mixes;
import com.renovator.mix.PickContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Large plain areas (≥12 equal blocks in one outer wall plane): matching mix palettes. */
public final class PlainRule implements DetailRule {
    @Override
    public String id() {
        return "plain";
    }

    static List<String> mixesFor(Family f) {
        String path = f.id().getPath();
        if (f.wood()) return List.of("renovator:weathered_wood");
        if (path.contains("deepslate")) return List.of("renovator:rough_deepslate", "renovator:rechiseled_deepslate");
        if (path.contains("sandstone")) return List.of("renovator:sandstone_worn");
        if (path.contains("nether_brick")) return List.of("renovator:nether_rough");
        return List.of("renovator:old_mossy", "renovator:castle_wall", "renovator:framed_stone", "renovator:rechiseled_castle");
    }

    @Override
    public void find(BuildingView v, DetailContext ctx, List<Suggestion> out) {
        Map<String, MixPalette> mixes = Mixes.builtIn().stream().collect(Collectors.toMap(MixPalette::id, m -> m));
        for (Walls.Plane pl : Walls.planes(v)) {
            Set<BlockPos> cells = new HashSet<>();
            for (List<BlockPos> col : pl.columns().values()) for (BlockPos p : col) if (Geo.isWallBody(v, p)) cells.add(p);
            Direction along = Direction.fromAxisAndDirection(pl.alongAxis(), Direction.AxisDirection.POSITIVE);
            Set<BlockPos> seen = new HashSet<>();
            List<BlockPos> ordered = cells.stream().sorted().toList();
            for (BlockPos start : ordered) {
                if (seen.contains(start)) continue;
                Block block = v.state(start).getBlock();
                List<BlockPos> comp = new ArrayList<>();
                ArrayDeque<BlockPos> q = new ArrayDeque<>();
                q.add(start);
                seen.add(start);
                while (!q.isEmpty()) {
                    BlockPos c = q.poll();
                    comp.add(c);
                    for (Direction n : new Direction[]{Direction.UP, Direction.DOWN, along, along.getOpposite()}) {
                        BlockPos np = c.relative(n);
                        if (cells.contains(np) && !seen.contains(np) && v.state(np).is(block)) {
                            seen.add(np);
                            q.add(np);
                        }
                    }
                }
                if (comp.size() < 12) continue;
                FamilyIndex.Member m = FamilyIndex.get().lookup(block);
                if (m == null) continue;
                LongOpenHashSet members = new LongOpenHashSet();
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (BlockPos c : comp) {
                    members.add(c.asLong());
                    minX = Math.min(minX, c.getX()); minY = Math.min(minY, c.getY()); minZ = Math.min(minZ, c.getZ());
                    maxX = Math.max(maxX, c.getX()); maxY = Math.max(maxY, c.getY()); maxZ = Math.max(maxZ, c.getZ());
                }
                PickContext pc = new PickContext(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), members);
                List<Variant> variants = new ArrayList<>();
                for (String id : mixesFor(m.family())) {
                    MixPalette mix = mixes.get(id);
                    if (mix == null || variants.size() >= 3) continue;
                    variants.add(new Variant("renovator.detail.plain.mix|" + mix.name(), ctx.variant(id(), "mix", e -> {
                        for (BlockPos c : comp) {
                            Family f = FamilyIndex.get().byId(mix.pick(ctx.seed(), c, pc));
                            Block to = f == null ? null : f.get(Shape.FULL);
                            if (to != null) e.replace(c, StateTransfer.transfer(v.state(c), to));
                        }
                    }).changes()));
                }
                Suggestion sg = DetailTypes.suggestion(id(), comp.get(0), variants);
                if (sg != null) out.add(sg);
            }
        }
    }
}
