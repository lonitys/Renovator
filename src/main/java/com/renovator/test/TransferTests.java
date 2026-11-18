package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.family.StateTransfer;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class TransferTests {
    private static final String T = RenovatorGameTests.T;

    @GameTest(template = T)
    public static void transfersProperties(GameTestHelper h) {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT).setValue(StairBlock.WATERLOGGED, true);
        BlockState spruce = StateTransfer.transfer(stairs, Blocks.SPRUCE_STAIRS);
        h.assertTrue(spruce.getBlock() == Blocks.SPRUCE_STAIRS && spruce.getValue(StairBlock.FACING) == Direction.EAST
                && spruce.getValue(StairBlock.HALF) == Half.TOP && spruce.getValue(StairBlock.SHAPE) == StairsShape.OUTER_LEFT
                && spruce.getValue(StairBlock.WATERLOGGED), "stairs state kept: " + spruce);

        BlockState dbl = StateTransfer.transfer(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), Blocks.STONE_BRICK_SLAB);
        h.assertTrue(dbl.getValue(SlabBlock.TYPE) == SlabType.DOUBLE, "double slab kept");

        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT).setValue(DoorBlock.OPEN, true).setValue(DoorBlock.FACING, Direction.WEST);
        BlockState sd = StateTransfer.transfer(door, Blocks.SPRUCE_DOOR);
        h.assertTrue(sd.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER && sd.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT
                && sd.getValue(DoorBlock.OPEN) && sd.getValue(DoorBlock.FACING) == Direction.WEST, "door state kept: " + sd);

        BlockState wall = StateTransfer.transfer(Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true), Blocks.STONE_BRICK_WALL);
        h.assertTrue(wall.getValue(WallBlock.NORTH_WALL) == WallSide.NONE, "fence booleans do not leak into wall sides");

        BlockState log = StateTransfer.transfer(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Blocks.BIRCH_LOG);
        h.assertTrue(log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "log axis kept");

        BlockState lantern = StateTransfer.transfer(Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), Blocks.SOUL_LANTERN);
        h.assertTrue(lantern.getValue(LanternBlock.HANGING), "lantern hanging kept");
        h.succeed();
    }
}
