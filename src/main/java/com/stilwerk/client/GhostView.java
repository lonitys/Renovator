package com.stilwerk.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * The world as it would look after the change: ghost states where something changes, the real world elsewhere.
 * Tesselating against it culls faces between ghost blocks and uses the real light and biome tints.
 */
final class GhostView implements BlockAndTintGetter {
    private final ClientLevel level;
    private final Long2ObjectOpenHashMap<BlockState> ghosts;

    GhostView(ClientLevel level, Long2ObjectOpenHashMap<BlockState> ghosts) {
        this.level = level;
        this.ghosts = ghosts;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState g = ghosts.get(pos.asLong());
        return g != null ? g : level.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return ghosts.containsKey(pos.asLong()) ? null : level.getBlockEntity(pos);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return level.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return level.getBlockTint(pos, resolver);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }
}
