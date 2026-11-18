package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.config.ServerConfig;
import com.stilwerk.core.Applier;
import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.CreativeGate;
import com.stilwerk.family.StateTransfer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class ApplierTests {
    private static final String T = StilwerkGameTests.T;
    private static final int Y = StilwerkGameTests.FLOOR + 1;

    static Change swap(GameTestHelper h, BlockPos rel, Block to, boolean reshape) {
        BlockPos abs = h.absolutePos(rel);
        BlockState old = h.getLevel().getBlockState(abs);
        return new Change(abs, old, StateTransfer.transfer(old, to), null, reshape);
    }

    @GameTest(template = T)
    public static void applierSetsAndSkips(GameTestHelper h) {
        for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(x, Y, 1), Blocks.OAK_PLANKS);
        Change a = swap(h, new BlockPos(1, Y, 1), Blocks.SPRUCE_PLANKS, false);
        Change stale = new Change(h.absolutePos(new BlockPos(2, Y, 1)), Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), null, false);
        Change c = swap(h, new BlockPos(3, Y, 1), Blocks.SPRUCE_PLANKS, false);
        ChangeSet applied = Applier.applyNow(h.getLevel(), ChangeSet.builder().add(a).add(stale).add(c).build(), CreativeGate.INSTANCE);
        h.assertTrue(applied.size() == 2, "2 applied, was " + applied.size());
        h.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(1, Y, 1));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(2, Y, 1));
        h.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(3, Y, 1));
        h.succeed();
    }

    @GameTest(template = T)
    public static void applierDoorStaysWhole(GameTestHelper h) {
        BlockPos lower = new BlockPos(4, Y, 4);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        h.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        ChangeSet set = ChangeSet.builder().group(List.of(swap(h, lower, Blocks.SPRUCE_DOOR, false), swap(h, lower.above(), Blocks.SPRUCE_DOOR, false))).build();
        ChangeSet applied = Applier.applyNow(h.getLevel(), set, CreativeGate.INSTANCE);
        h.assertTrue(applied.size() == 2, "both halves applied");
        h.assertBlockPresent(Blocks.SPRUCE_DOOR, lower);
        h.assertBlockPresent(Blocks.SPRUCE_DOOR, lower.above());
        h.assertTrue(h.getBlockState(lower.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "upper half kept");
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 60)
    public static void applierBudget(GameTestHelper h) {
        ChangeSet.Builder b = ChangeSet.builder();
        for (int x = 1; x <= 5; x++) {
            h.setBlock(new BlockPos(x, Y, 6), Blocks.OAK_PLANKS);
            b.add(swap(h, new BlockPos(x, Y, 6), Blocks.SPRUCE_PLANKS, false));
        }
        ServerConfig.budgetOverride = 2;
        AtomicInteger done = new AtomicInteger(-1);
        Applier.submit(new Applier.Job(UUID.randomUUID(), h.getLevel(), b.build(), CreativeGate.INSTANCE, s -> done.set(s.size()), null));
        h.runAfterDelay(1, () -> {
            int n = 0;
            for (int x = 1; x <= 5; x++) if (h.getBlockState(new BlockPos(x, Y, 6)).is(Blocks.SPRUCE_PLANKS)) n++;
            // other tests may have jobs queued in front of this one, so only "not all at once" is certain
            h.assertTrue(n < 5, "spread over ticks, " + n + " after one tick");
        });
        h.succeedWhen(() -> {
            h.assertTrue(done.get() == 5, "job done with 5");
            ServerConfig.budgetOverride = -1;
        });
    }

    @GameTest(template = T)
    public static void applierReshape(GameTestHelper h) {
        for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(x, Y, 9), Blocks.OAK_FENCE);
        ChangeSet.Builder b = ChangeSet.builder();
        for (int x = 1; x <= 3; x++) b.add(swap(h, new BlockPos(x, Y, 9), Blocks.STONE_BRICK_WALL, true));
        Applier.applyNow(h.getLevel(), b.build(), CreativeGate.INSTANCE);
        BlockState mid = h.getBlockState(new BlockPos(2, Y, 9));
        h.assertTrue(mid.is(Blocks.STONE_BRICK_WALL), "is wall");
        h.assertTrue(mid.getValue(WallBlock.EAST_WALL) != WallSide.NONE && mid.getValue(WallBlock.WEST_WALL) != WallSide.NONE,
                "walls connect after reshape: " + mid);
        h.succeed();
    }
}
