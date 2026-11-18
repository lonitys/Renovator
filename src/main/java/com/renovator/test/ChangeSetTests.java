package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.core.Costs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class ChangeSetTests {
    private static final String T = RenovatorGameTests.T;

    @GameTest(template = T)
    public static void saveLoadInverse(GameTestHelper h) {
        BlockState doorLow = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState doorUp = doorLow.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        CompoundTag sign = new CompoundTag();
        sign.putString("marker", "hello");
        ChangeSet set = ChangeSet.builder()
                .add(new Change(new BlockPos(1, 2, 3), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), null, false))
                .group(List.of(new Change(new BlockPos(5, 5, 5), doorLow, Blocks.SPRUCE_DOOR.defaultBlockState(), null, false),
                        new Change(new BlockPos(5, 6, 5), doorUp, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), null, false)))
                .add(new Change(new BlockPos(-7, 64, 9), Blocks.OAK_SIGN.defaultBlockState(), Blocks.SPRUCE_SIGN.defaultBlockState(), sign, true))
                .build();
        h.assertTrue(set.size() == 4 && set.groups().size() == 3, "sizes");
        ChangeSet loaded = ChangeSet.load(set.save(), BuiltInRegistries.BLOCK.asLookup());
        h.assertTrue(loaded.equals(set), "round trip equal");
        ChangeSet inv = set.inverse();
        Change first = inv.groups().get(0).get(0);
        h.assertTrue(first.oldState().is(Blocks.SPRUCE_SIGN) && first.newState().is(Blocks.OAK_SIGN), "inverse reverses order and states");
        h.assertTrue(first.blockEntity() != null && first.blockEntity().getString("marker").equals("hello"), "inverse keeps sign data");
        h.succeed();
    }

    @GameTest(template = T)
    public static void costs(GameTestHelper h) {
        h.assertTrue(Costs.count(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)) == 2, "double slab 2");
        h.assertTrue(Costs.count(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)) == 0, "door upper 0");
        h.assertTrue(Costs.count(Blocks.OAK_DOOR.defaultBlockState()) == 1, "door lower 1");
        h.assertTrue(Costs.count(Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH)) == 0, "bed head 0");
        h.assertTrue(Costs.count(Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3)) == 3, "3 candles");
        h.assertTrue(Costs.count(Blocks.OAK_PLANKS.defaultBlockState()) == 1, "planks 1");
        h.assertTrue(Costs.count(Blocks.AIR.defaultBlockState()) == 0, "air 0");
        h.assertTrue(Costs.item(Blocks.OAK_WALL_SIGN.defaultBlockState()) == net.minecraft.world.item.Items.OAK_SIGN, "wall sign costs a sign");
        h.succeed();
    }
}
