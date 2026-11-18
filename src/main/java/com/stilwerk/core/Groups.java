package com.stilwerk.core;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Two-block blocks (doors, tall plants, beds) must change together: one primary half owns the pair. */
public final class Groups {
    private Groups() {}

    public static Optional<BlockPos> partner(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return Optional.of(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
        }
        if (state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
            Direction facing = state.getValue(BedBlock.FACING);
            return Optional.of(state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite()));
        }
        return Optional.empty();
    }

    /** True for single blocks and for the owning half (lower half, bed foot). */
    public static boolean isPrimary(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
        }
        if (state.hasProperty(BedBlock.PART)) return state.getValue(BedBlock.PART) == BedPart.FOOT;
        return true;
    }
}
