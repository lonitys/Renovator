package com.renovator.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One block change. {@code reshape} marks changes whose shape changed (fence ↔ wall): their connections
 * are recomputed after applying. {@code blockEntity} is loaded into the new block entity, if any.
 */
public record Change(BlockPos pos, BlockState oldState, BlockState newState, @Nullable CompoundTag blockEntity, boolean reshape) {
    public Change {
        pos = pos.immutable();
    }

    public Change inverse() {
        return new Change(pos, newState, oldState, blockEntity, reshape);
    }
}
