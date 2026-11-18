package com.stilwerk.blueprint;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Rotates and mirrors blueprints like vanilla structures: mirror first, then rotate (clockwise seen from above). */
public final class Transform {
    private Transform() {}

    public static Vec3i size(Vec3i size, Rotation rot) {
        return rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90
                ? new Vec3i(size.getZ(), size.getY(), size.getX()) : size;
    }

    public static Blueprint apply(Blueprint bp, Rotation rot, Mirror mirror) {
        if (rot == Rotation.NONE && mirror == Mirror.NONE) return bp;
        Vec3i s = bp.size();
        int sx = s.getX(), sz = s.getZ();
        Blueprint.Builder b = new Blueprint.Builder(size(s, rot), bp.meta());
        bp.forEach((p, state, be) -> {
            int x = p.getX(), z = p.getZ();
            if (mirror == Mirror.LEFT_RIGHT) z = sz - 1 - z;
            else if (mirror == Mirror.FRONT_BACK) x = sx - 1 - x;
            int nx, nz;
            switch (rot) {
                case CLOCKWISE_90 -> { nx = sz - 1 - z; nz = x; }
                case COUNTERCLOCKWISE_90 -> { nx = z; nz = sx - 1 - x; }
                case CLOCKWISE_180 -> { nx = sx - 1 - x; nz = sz - 1 - z; }
                default -> { nx = x; nz = z; }
            }
            b.set(nx, p.getY(), nz, turn(state, rot, mirror), be);
        });
        return b.build();
    }

    /** Mirrors and rotates a state; blocks whose facing cannot take every direction (some modded furniture) stay as they are. */
    static net.minecraft.world.level.block.state.BlockState turn(net.minecraft.world.level.block.state.BlockState s, Rotation rot, Mirror mirror) {
        try {
            return s.mirror(mirror).rotate(rot);
        } catch (RuntimeException e) {
            return s;
        }
    }
}
