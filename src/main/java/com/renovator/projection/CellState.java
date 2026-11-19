package com.renovator.projection;

import com.renovator.core.Change;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** What a projection cell looks like in the world right now. */
public enum CellState {
    /** The target block stands there (orientation right; shape and connections may differ). */
    DONE,
    /** Still to build: the cell is free, or the expected old block of a swap is still there. */
    OPEN,
    /** Something else stands there. */
    BLOCKED;

    /** Properties that say how a block was placed; others (shape, connections, waterlogged, open ...) follow the world. */
    static final Set<String> ORIENTATION = Set.of("facing", "axis", "half", "type", "part", "hinge", "face", "rotation",
            "attachment", "orientation", "hanging");

    public static CellState of(BlockState world, Change cell) {
        return of(world, cell.newState(), cell.oldState());
    }

    public static CellState of(BlockState world, BlockState target, BlockState old) {
        if (matches(world, target)) return DONE;
        if (world.isAir() || world.canBeReplaced()) return OPEN;
        if (!old.isAir() && world.is(old.getBlock())) return OPEN;
        return BLOCKED;
    }

    /** Same block and the same placement properties. */
    public static boolean matches(BlockState world, BlockState target) {
        if (world.getBlock() != target.getBlock()) return false;
        for (Property<?> p : target.getProperties()) {
            if (ORIENTATION.contains(p.getName()) && !world.getValue(p).equals(target.getValue(p))) return false;
        }
        return true;
    }
}
