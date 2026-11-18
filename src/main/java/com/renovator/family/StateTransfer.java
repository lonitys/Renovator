package com.renovator.family;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Moves a block's state (facing, half, shape, waterlogged ...) onto another block by property name. */
public final class StateTransfer {
    private StateTransfer() {}

    public static BlockState transfer(BlockState from, Block to) {
        BlockState out = to.defaultBlockState();
        for (Property<?> p : from.getProperties()) {
            Property<?> q = to.getStateDefinition().getProperty(p.getName());
            if (q != null) out = copy(out, q, valueName(from, p));
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState s, Property<T> q, String name) {
        return q.getValue(name).map(v -> s.setValue(q, v)).orElse(s);
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }
}
