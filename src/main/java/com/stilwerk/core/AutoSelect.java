package com.stilwerk.core;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Selects a building by flood fill over connected built (non-air, non-natural) blocks. */
public final class AutoSelect {
    private AutoSelect() {}

    public static ResolvedSelection flood(BlockGetter level, BlockPos start, int radius, int limit) {
        NatureFilter nature = new NatureFilter(level);
        LongArrayList found = new LongArrayList();
        if (!built(level, nature, start)) return ResolvedSelection.ofPositions(found, false);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        queue.enqueue(start.asLong());
        seen.add(start.asLong());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long cur = queue.dequeueLong();
            if (found.size() >= limit) return ResolvedSelection.ofPositions(new LongArrayList(), true);
            found.add(cur);
            p.set(cur);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        n.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                        if (Math.abs(n.getX() - start.getX()) > radius || Math.abs(n.getY() - start.getY()) > radius
                                || Math.abs(n.getZ() - start.getZ()) > radius) continue;
                        long key = n.asLong();
                        if (seen.contains(key)) continue;
                        seen.add(key);
                        if (built(level, nature, n)) queue.enqueue(key);
                    }
        }
        return ResolvedSelection.ofPositions(found, false);
    }

    private static boolean built(BlockGetter level, NatureFilter nature, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && !nature.isNatural(pos, s);
    }
}
