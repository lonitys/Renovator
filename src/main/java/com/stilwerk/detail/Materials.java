package com.stilwerk.detail;

import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.family.Shape;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks for decoration, taken from the building's own wood and stone families (with sensible fallbacks). */
public final class Materials {
    private final Family wood, stone;
    private final Family oak, stoneBricks;

    public Materials(Family wood, Family stone) {
        this.wood = wood;
        this.stone = stone;
        this.oak = FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace("oak"));
        this.stoneBricks = FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace("stone_bricks"));
    }

    private static Block pick(Family f, Family fallback, Shape s) {
        Block b = f == null ? null : f.get(s);
        return b != null ? b : fallback.get(s);
    }

    public Family wood() {
        return wood;
    }

    public Family stone() {
        return stone;
    }

    public BlockState fence() {
        return pick(wood, oak, Shape.FENCE).defaultBlockState();
    }

    public BlockState trapdoor() {
        return pick(wood, oak, Shape.TRAPDOOR).defaultBlockState();
    }

    public BlockState woodStairs() {
        return pick(wood, oak, Shape.STAIRS).defaultBlockState();
    }

    public BlockState woodSlab() {
        return pick(wood, oak, Shape.SLAB).defaultBlockState();
    }

    public BlockState log() {
        return axisY(pick(wood, oak, Shape.LOG).defaultBlockState());
    }

    public BlockState strippedLog() {
        Block b = wood.get(Shape.STRIPPED_LOG);
        return axisY((b != null ? b : oak.get(Shape.STRIPPED_LOG)).defaultBlockState());
    }

    public BlockState stoneFull() {
        return pick(stone, stoneBricks, Shape.FULL).defaultBlockState();
    }

    public BlockState stoneStairs() {
        return pick(stone, stoneBricks, Shape.STAIRS).defaultBlockState();
    }

    public BlockState stoneWall() {
        Block b = stone == null ? null : stone.get(Shape.WALL);
        return (b != null ? b : Blocks.STONE_BRICK_WALL).defaultBlockState();
    }

    private static BlockState axisY(BlockState s) {
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : s;
    }
}
