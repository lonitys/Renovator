package com.renovator.detail;

import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.core.Protection;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Builds one variant: {@link #put} fills free cells, {@link #replace} swaps built blocks. Each position once. */
public final class Edit {
    private final BuildingView view;
    private final Long2ObjectLinkedOpenHashMap<Change> changes = new Long2ObjectLinkedOpenHashMap<>();
    private int depth;
    private boolean groupFailed;
    private final it.unimi.dsi.fastutil.longs.LongArrayList groupKeys = new it.unimi.dsi.fastutil.longs.LongArrayList();

    Edit(BuildingView view) {
        this.view = view;
    }

    /**
     * Runs {@code body} as one unit: when any of its put/replace calls fails, none of them is kept
     * (a lantern without its post, a single shutter ... would look misplaced). Returns true when kept.
     */
    public boolean atomic(java.util.function.Consumer<Edit> body) {
        boolean outer = depth == 0;
        int mark = groupKeys.size();
        depth++;
        try {
            body.accept(this);
        } finally {
            depth--;
        }
        if (groupFailed && outer) {
            for (int i = mark; i < groupKeys.size(); i++) changes.remove(groupKeys.getLong(i));
            groupKeys.size(mark);
            groupFailed = false;
            return false;
        }
        if (outer) groupKeys.size(mark);
        return !groupFailed;
    }

    private boolean fail() {
        if (depth > 0) groupFailed = true;
        return false;
    }

    private static boolean connects(BlockState s) {
        Block b = s.getBlock();
        return b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof ChainBlock;
    }

    /** Places into a free cell (air or replaceable, not protected). Returns false when the cell is taken. */
    public boolean put(BlockPos p, BlockState s) {
        long k = p.asLong();
        if (changes.containsKey(k) || !view.free(p)) return fail();
        changes.put(k, new Change(p, view.state(p), s, null, connects(s)));
        if (depth > 0) groupKeys.add(k);
        return true;
    }

    /** Swaps a built, unprotected block. */
    public boolean replace(BlockPos p, BlockState s) {
        long k = p.asLong();
        BlockState cur = view.state(p);
        if (changes.containsKey(k) || !view.built(p) || cur == s || Protection.isProtected(view.level(), p, cur)) return fail();
        changes.put(k, new Change(p, cur, s, null, connects(s) || connects(cur)));
        if (depth > 0) groupKeys.add(k);
        return true;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public ChangeSet build() {
        ChangeSet.Builder b = ChangeSet.builder();
        for (Change c : changes.values()) b.add(c);
        return b.build();
    }
}
