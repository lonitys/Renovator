package com.stilwerk.detail;

import com.stilwerk.core.NatureFilter;
import com.stilwerk.core.Protection;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.family.Shape;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the detail rules know about a building: which blocks are built, which free cells are outside
 * (reachable from the border of the analysed box) and its dominant wood and stone families.
 */
public final class BuildingView {
    private static final int MARGIN = 2;

    private final BlockGetter level;
    private final ResolvedSelection sel;
    private final BoundingBox box;
    private final NatureFilter nature;
    private final LongOpenHashSet outside = new LongOpenHashSet();
    private final Family wood, stone;

    public BuildingView(BlockGetter level, ResolvedSelection sel) {
        this.level = level;
        this.sel = sel;
        this.nature = new NatureFilter(level);
        BoundingBox b = sel.bounds();
        this.box = new BoundingBox(b.minX() - MARGIN, b.minY() - MARGIN, b.minZ() - MARGIN, b.maxX() + MARGIN, b.maxY() + MARGIN, b.maxZ() + MARGIN);
        floodOutside();
        Map<Family, Integer> woods = new HashMap<>(), stones = new HashMap<>();
        sel.forEach(p -> {
            if (!built(p)) return;
            FamilyIndex.Member m = FamilyIndex.get().lookup(level.getBlockState(p).getBlock());
            if (m == null) return;
            Family f = m.family();
            if (f.wood()) woods.merge(f, 1, Integer::sum);
            else if (f.has(Shape.FULL) && f.has(Shape.STAIRS)) stones.merge(f, 1, Integer::sum);
        });
        this.wood = best(woods, "oak");
        this.stone = best(stones, "stone_bricks");
    }

    private static Family best(Map<Family, Integer> counts, String fallback) {
        Family best = null;
        int n = -1;
        for (Map.Entry<Family, Integer> e : counts.entrySet()) {
            if (e.getValue() > n || (e.getValue() == n && e.getKey().id().compareTo(best.id()) < 0)) {
                best = e.getKey();
                n = e.getValue();
            }
        }
        return best != null ? best : FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace(fallback));
    }

    private void floodOutside() {
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    boolean border = x == box.minX() || x == box.maxX() || y == box.minY() || y == box.maxY() || z == box.minZ() || z == box.maxZ();
                    if (!border) continue;
                    m.set(x, y, z);
                    if (passable(m) && outside.add(m.asLong())) q.enqueue(m.asLong());
                }
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!q.isEmpty()) {
            m.set(q.dequeueLong());
            for (Direction d : Direction.values()) {
                n.setWithOffset(m, d);
                if (!box.isInside(n) || outside.contains(n.asLong()) || !passable(n)) continue;
                outside.add(n.asLong());
                q.enqueue(n.asLong());
            }
        }
    }

    private boolean passable(BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.canBeReplaced();
    }

    public BlockGetter level() {
        return level;
    }

    public ResolvedSelection selection() {
        return sel;
    }

    public BoundingBox bounds() {
        return sel.bounds();
    }

    public BlockState state(BlockPos p) {
        return level.getBlockState(p);
    }

    /** In the selection, not air, not nature. */
    public boolean built(BlockPos p) {
        if (!sel.contains(p)) return false;
        BlockState s = level.getBlockState(p);
        return !s.isAir() && !nature.isNatural(p, s);
    }

    /** A free cell connected to the outside of the analysed box (or beyond it). */
    public boolean outside(BlockPos p) {
        if (!box.isInside(p)) return passable(p);
        return outside.contains(p.asLong());
    }

    /** Air or replaceable, and not protected: a cell a suggestion may fill. */
    public boolean free(BlockPos p) {
        BlockState s = level.getBlockState(p);
        return (s.isAir() || s.canBeReplaced()) && !Protection.isProtected(level, p, s);
    }

    public Family wood() {
        return wood;
    }

    public Family stone() {
        return stone;
    }

    public FamilyIndex.Member member(BlockPos p) {
        return FamilyIndex.get().lookup(level.getBlockState(p).getBlock());
    }
}
