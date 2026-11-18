package com.stilwerk.core;

import com.stilwerk.StilwerkTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Existing blocks Stilwerk never changes: unbreakable ones, block entities other than signs, and the protected tag. */
public final class Protection {
    private Protection() {}

    public static boolean isProtected(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0) return true;
        if (state.is(StilwerkTags.PROTECTED)) return true;
        return state.hasBlockEntity() && !(state.getBlock() instanceof SignBlock);
    }
}
