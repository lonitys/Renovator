package com.stilwerk.detail.rule;

import com.stilwerk.detail.BuildingView;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.family.Shape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/** Geometry helpers shared by the detail rules. */
final class Geo {
    private Geo() {}

    static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** All built positions of the selection (immutable copies), in selection order. */
    static List<BlockPos> built(BuildingView v) {
        List<BlockPos> out = new ArrayList<>();
        v.selection().forEach(p -> {
            if (v.built(p)) out.add(p.immutable());
        });
        return out;
    }

    /** The single horizontal direction in which the block faces the outside, or null (none or several). */
    static @Nullable Direction outsideFace(BuildingView v, BlockPos p) {
        Direction found = null;
        for (Direction d : HORIZONTAL) {
            if (v.outside(p.relative(d))) {
                if (found != null) return null;
                found = d;
            }
        }
        return found;
    }

    static boolean isFull(BuildingView v, BlockPos p) {
        FamilyIndex.Member m = v.member(p);
        return m != null && m.shape() == Shape.FULL && v.state(p).isCollisionShapeFullBlock(v.level(), p);
    }

    static boolean isGlass(BlockState s) {
        return s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES);
    }

    static boolean isBottomStair(BlockState s) {
        return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM;
    }

    /** Coordinate of p along the positive direction of an axis. */
    static int along(BlockPos p, Direction.Axis a) {
        return a.choose(p.getX(), p.getY(), p.getZ());
    }

    /** An open trapdoor whose panel lies on the {@code panelSide} face of its cell. */
    static BlockState openTrapdoor(BlockState trapdoor, Direction panelSide) {
        return trapdoor.setValue(TrapDoorBlock.FACING, panelSide.getOpposite()).setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM);
    }

    static BlockState stairs(BlockState stairs, Direction back, Half half) {
        return stairs.setValue(StairBlock.FACING, back).setValue(StairBlock.HALF, half);
    }

    static BlockState hanging(BlockState lantern, boolean hanging) {
        return lantern.hasProperty(BlockStateProperties.HANGING) ? lantern.setValue(BlockStateProperties.HANGING, hanging) : lantern;
    }
}
