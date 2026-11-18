package com.renovator.blueprint;

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

    /**
     * Mirrors and rotates a state. Some modded blocks do not turn themselves, or only allow two facings (a symmetric
     * desk: north/east); their horizontal "facing" is then set here, using the opposite direction when the exact one
     * does not exist.
     */
    static net.minecraft.world.level.block.state.BlockState turn(net.minecraft.world.level.block.state.BlockState s, Rotation rot, Mirror mirror) {
        net.minecraft.world.level.block.state.BlockState out;
        try {
            out = s.mirror(mirror).rotate(rot);
        } catch (RuntimeException e) {
            out = s;
        }
        for (net.minecraft.world.level.block.state.properties.Property<?> p : s.getProperties()) {
            if (!p.getName().equals("facing") || !(s.getValue(p) instanceof net.minecraft.core.Direction from) || !from.getAxis().isHorizontal()) continue;
            net.minecraft.core.Direction want = rot.rotate(mirror.mirror(from));
            if (out.getValue(p) == want) break;
            @SuppressWarnings("unchecked")
            net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction> dp =
                    (net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction>) p;
            if (dp.getPossibleValues().contains(want)) out = out.setValue(dp, want);
            else if (dp.getPossibleValues().contains(want.getOpposite())) out = out.setValue(dp, want.getOpposite());
            break;
        }
        return out;
    }
}
