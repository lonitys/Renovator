package com.stilwerk.core;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** A concrete set of positions. Iteration order is y, then z, then x ascending (deterministic on both sides). */
public final class ResolvedSelection {
    private final BoundingBox bounds;
    private final @Nullable LongArrayList ordered; // null for boxes
    private final @Nullable LongOpenHashSet members;
    private final boolean truncated;

    private ResolvedSelection(BoundingBox bounds, @Nullable LongArrayList ordered, boolean truncated) {
        this.bounds = bounds;
        this.ordered = ordered;
        this.members = ordered == null ? null : new LongOpenHashSet(ordered);
        this.truncated = truncated;
    }

    public static ResolvedSelection ofBox(BoundingBox box, int limit) {
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > limit) return new ResolvedSelection(box, new LongArrayList(), true);
        return new ResolvedSelection(box, null, false);
    }

    /** Positions in any order; they are sorted here. */
    public static ResolvedSelection ofPositions(LongArrayList positions, boolean truncated) {
        LongArrayList sorted = new LongArrayList(positions);
        sorted.sort((a, b) -> {
            int c = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
            if (c != 0) return c;
            c = Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
            return c != 0 ? c : Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
        });
        BoundingBox bounds;
        if (sorted.isEmpty()) {
            bounds = new BoundingBox(0, 0, 0, 0, 0, 0);
        } else {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < sorted.size(); i++) {
                long p = sorted.getLong(i);
                int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            bounds = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return new ResolvedSelection(bounds, sorted, truncated);
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public boolean isBox() {
        return ordered == null;
    }

    public int size() {
        if (ordered != null) return ordered.size();
        return bounds.getXSpan() * bounds.getYSpan() * bounds.getZSpan();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** True when the selection was larger than the limit (and is then empty). */
    public boolean truncated() {
        return truncated;
    }

    public boolean contains(BlockPos p) {
        if (members != null) return members.contains(p.asLong());
        return bounds.isInside(p);
    }

    /** Visits every position; the mutable position must not be kept. */
    public void forEach(Consumer<BlockPos.MutableBlockPos> visitor) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (ordered != null) {
            for (int i = 0; i < ordered.size(); i++) visitor.accept(m.set(ordered.getLong(i)));
            return;
        }
        for (int y = bounds.minY(); y <= bounds.maxY(); y++)
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                    visitor.accept(m.set(x, y, z));
    }
}
