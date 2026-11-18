package com.stilwerk.core;

import com.stilwerk.StilwerkTags;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a block is terrain/vegetation rather than something a player built.
 * One instance per planning run: tree decisions are cached per log position.
 */
public final class NatureFilter {
    private static final int MAX_TREE = 512;

    private final BlockGetter level;
    private final Long2BooleanOpenHashMap treeCache = new Long2BooleanOpenHashMap();

    public NatureFilter(BlockGetter level) {
        this.level = level;
    }

    public boolean isNatural(BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(StilwerkTags.NATURAL)) return true;
        if (state.hasProperty(LeavesBlock.PERSISTENT)) return !state.getValue(LeavesBlock.PERSISTENT);
        if (state.is(BlockTags.LOGS)) return isTreeLog(pos);
        return false;
    }

    /** A log is part of a tree when its connected log cluster touches natural (non-persistent) leaves or wart blocks. */
    private boolean isTreeLog(BlockPos start) {
        long key = start.asLong();
        if (treeCache.containsKey(key)) return treeCache.get(key);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList cluster = new LongArrayList();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        queue.enqueue(key);
        seen.add(key);
        boolean tree = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && cluster.size() < MAX_TREE) {
            long cur = queue.dequeueLong();
            cluster.add(cur);
            p.set(cur);
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                BlockState ns = level.getBlockState(n);
                if (ns.hasProperty(LeavesBlock.PERSISTENT) && !ns.getValue(LeavesBlock.PERSISTENT)) tree = true;
                else if (ns.is(BlockTags.WART_BLOCKS)) tree = true;
                else if (ns.is(BlockTags.LOGS) && seen.add(n.asLong())) queue.enqueue(n.asLong());
            }
            if (tree) break;
        }
        for (int i = 0; i < cluster.size(); i++) treeCache.put(cluster.getLong(i), tree);
        if (tree) {
            // everything still queued belongs to the same tree
            while (!queue.isEmpty()) treeCache.put(queue.dequeueLong(), true);
        }
        return tree;
    }
}
