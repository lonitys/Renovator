package com.renovator.interior;

import com.renovator.detail.rule.GeoPublic;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** Finds the room around a clicked block by flooding its free cells (with size limits that also catch open rooms). */
public final class RoomDetector {
    private RoomDetector() {}

    public static final int MAX_SPAN_XZ = 24, MAX_SPAN_Y = 12, MAX_CELLS = 4000;

    public record Result(@Nullable Room room, @Nullable String error, @Nullable BlockPos at) {}

    static boolean passable(BlockGetter level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.canBeReplaced() || s.getBlock() instanceof CarpetBlock;
    }

    public static Result detect(BlockGetter level, BlockPos clicked, Direction face) {
        BlockPos start = clicked.relative(face);
        if (!passable(level, start)) start = clicked.above();
        if (!passable(level, start)) return new Result(null, "renovator.interior.no_space", clicked);
        LongOpenHashSet air = new LongOpenHashSet();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        air.add(start.asLong());
        q.enqueue(start.asLong());
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos(), n = new BlockPos.MutableBlockPos();
        while (!q.isEmpty()) {
            c.set(q.dequeueLong());
            for (Direction d : Direction.values()) {
                n.setWithOffset(c, d);
                long k = n.asLong();
                if (air.contains(k) || !passable(level, n)) continue;
                if (level.isOutsideBuildHeight(n)) return new Result(null, "renovator.interior.open", n.immutable());
                air.add(k);
                minX = Math.min(minX, n.getX()); minY = Math.min(minY, n.getY()); minZ = Math.min(minZ, n.getZ());
                maxX = Math.max(maxX, n.getX()); maxY = Math.max(maxY, n.getY()); maxZ = Math.max(maxZ, n.getZ());
                if (maxX - minX >= MAX_SPAN_XZ || maxZ - minZ >= MAX_SPAN_XZ || maxY - minY >= MAX_SPAN_Y || air.size() > MAX_CELLS) {
                    return new Result(null, "renovator.interior.open", n.immutable());
                }
                q.enqueue(k);
            }
        }
        // floor: free cells with solid ground below, on the lowest such level
        int floorY = Integer.MAX_VALUE;
        List<Long> candidates = new ArrayList<>();
        for (long k : air) {
            BlockPos p = BlockPos.of(k);
            BlockPos b = p.below();
            if (air.contains(b.asLong())) continue;
            BlockState below = level.getBlockState(b);
            if (!below.isFaceSturdy(level, b, Direction.UP)) continue;
            candidates.add(k);
            floorY = Math.min(floorY, p.getY());
        }
        if (candidates.isEmpty()) return new Result(null, "renovator.interior.no_floor", start);
        LongOpenHashSet floor = new LongOpenHashSet();
        for (long k : candidates) if (BlockPos.getY(k) == floorY) floor.add(k);
        Long2IntOpenHashMap heights = new Long2IntOpenHashMap();
        Map<Long, EnumSet<Direction>> walls = new HashMap<>();
        for (long k : floor) {
            BlockPos p = BlockPos.of(k);
            int h = 0;
            while (air.contains(p.above(h).asLong())) h++;
            heights.put(k, h);
            EnumSet<Direction> w = EnumSet.noneOf(Direction.class);
            for (Direction d : Direction.Plane.HORIZONTAL) if (!air.contains(p.relative(d).asLong())) w.add(d);
            if (!w.isEmpty()) walls.put(k, w);
        }
        // doors on the floor level and the cells kept free in front of them
        List<BlockPos> doors = new ArrayList<>();
        LongOpenHashSet doorClear = new LongOpenHashSet();
        LongOpenHashSet windowFloor = new LongOpenHashSet();
        List<BlockPos> windows = new ArrayList<>();
        for (long k : floor) {
            BlockPos p = BlockPos.of(k);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = p.relative(d);
                BlockState s = level.getBlockState(side);
                if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    if (!doors.contains(side)) doors.add(side.immutable());
                    doorClear.add(k);
                    BlockPos in = p.relative(d.getOpposite());
                    if (floor.contains(in.asLong())) doorClear.add(in.asLong());
                }
                for (int up = 1; up <= 2; up++) {
                    BlockPos w = side.above(up);
                    if (GeoPublic.isGlass(level.getBlockState(w))) {
                        windowFloor.add(k);
                        if (!windows.contains(w)) windows.add(w.immutable());
                    }
                }
            }
        }
        return new Result(new Room(start.immutable(), air, floor, floorY, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), heights, walls,
                doors, doorClear, windows, windowFloor), null, null);
    }
}
