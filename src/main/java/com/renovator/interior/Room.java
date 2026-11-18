package com.renovator.interior;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A detected room. {@code air}: all free cells; {@code floor}: free cells on the main floor level with solid ground;
 * {@code heights}: free cells above each floor cell (itself included); {@code walls}: horizontal directions in which a
 * floor cell touches something that is not room; {@code doorClear}: floor cells kept free in front of doors;
 * {@code windowFloor}: floor cells right under a window.
 */
public record Room(BlockPos start, LongOpenHashSet air, LongOpenHashSet floor, int floorY, BoundingBox bounds,
                   Long2IntOpenHashMap heights, Map<Long, EnumSet<Direction>> walls, List<BlockPos> doors,
                   LongOpenHashSet doorClear, List<BlockPos> windows, LongOpenHashSet windowFloor) {

    public int height(long floorCell) {
        return heights.getOrDefault(floorCell, 0);
    }

    public EnumSet<Direction> wallsAt(long floorCell) {
        EnumSet<Direction> w = walls.get(floorCell);
        return w == null ? EnumSet.noneOf(Direction.class) : w;
    }

    public int width() {
        return bounds.getXSpan();
    }

    public int depth() {
        return bounds.getZSpan();
    }
}
