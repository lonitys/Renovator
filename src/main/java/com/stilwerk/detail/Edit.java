package com.stilwerk.detail;

import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.Protection;
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

    Edit(BuildingView view) {
        this.view = view;
    }

    private static boolean connects(BlockState s) {
        Block b = s.getBlock();
        return b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof ChainBlock;
    }

    /** Places into a free cell (air or replaceable, not protected). Returns false when the cell is taken. */
    public boolean put(BlockPos p, BlockState s) {
        long k = p.asLong();
        if (changes.containsKey(k) || !view.free(p)) return false;
        changes.put(k, new Change(p, view.state(p), s, null, connects(s)));
        return true;
    }

    /** Swaps a built, unprotected block. */
    public boolean replace(BlockPos p, BlockState s) {
        long k = p.asLong();
        BlockState cur = view.state(p);
        if (changes.containsKey(k) || !view.built(p) || cur == s || Protection.isProtected(view.level(), p, cur)) return false;
        changes.put(k, new Change(p, cur, s, null, connects(s) || connects(cur)));
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
