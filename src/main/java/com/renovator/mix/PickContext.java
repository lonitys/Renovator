package com.renovator.mix;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** What a palette may know about the positions it fills: their bounds and membership (for edges). */
public final class PickContext {
    private final BoundingBox bounds;
    private final LongSet members;
    private long noiseSeed;
    private SimplexNoise noise;

    public PickContext(BoundingBox bounds, LongSet members) {
        this.bounds = bounds;
        this.members = members;
    }

    /** 0 at the low end of the bounds along the axis, 1 at the high end. */
    public double along(Direction.Axis axis, BlockPos p) {
        int min = axis.choose(bounds.minX(), bounds.minY(), bounds.minZ());
        int max = axis.choose(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (max <= min) return 0.5;
        return (axis.choose(p.getX(), p.getY(), p.getZ()) - min) / (double) (max - min);
    }

    /** An edge block is missing a neighbour along at least two axes. */
    public boolean isEdge(BlockPos p) {
        int open = 0;
        for (Direction.Axis a : Direction.Axis.values()) {
            Direction pos = Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE);
            if (!members.contains(p.relative(pos).asLong()) || !members.contains(p.relative(pos.getOpposite()).asLong())) open++;
        }
        return open >= 2;
    }

    public SimplexNoise noise(long seed) {
        if (noise == null || noiseSeed != seed) {
            noise = new SimplexNoise(new LegacyRandomSource(seed));
            noiseSeed = seed;
        }
        return noise;
    }
}
