package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.projection.CellState;
import com.renovator.projection.Projection;
import com.renovator.projection.ProjectionStore;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class ProjectionTests {
    private static final String T = RenovatorGameTests.T;
    static final int Y = RenovatorGameTests.FLOOR + 1;

    static final BlockState STAIRS_N = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);

    static Change place(BlockPos p, BlockState target) {
        return new Change(p, Blocks.AIR.defaultBlockState(), target, null, false);
    }

    @GameTest(template = T)
    public static void cellStates(GameTestHelper h) {
        BlockPos p = h.absolutePos(new BlockPos(2, Y, 2));
        Change stairs = place(p, STAIRS_N);
        h.assertTrue(CellState.of(Blocks.AIR.defaultBlockState(), stairs) == CellState.OPEN, "air is open");
        h.assertTrue(CellState.of(Blocks.SHORT_GRASS.defaultBlockState(), stairs) == CellState.OPEN, "grass is replaceable: open");
        h.assertTrue(CellState.of(STAIRS_N.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), stairs) == CellState.DONE, "shape ignored: done");
        h.assertTrue(CellState.of(STAIRS_N.setValue(StairBlock.FACING, Direction.EAST), stairs) == CellState.BLOCKED, "wrong facing: blocked");
        h.assertTrue(CellState.of(Blocks.STONE.defaultBlockState(), stairs) == CellState.BLOCKED, "other block: blocked");
        Change swap = new Change(p, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), null, false);
        h.assertTrue(CellState.of(Blocks.OAK_PLANKS.defaultBlockState(), swap) == CellState.OPEN, "old block still there: open");
        h.assertTrue(CellState.of(Blocks.SPRUCE_PLANKS.defaultBlockState(), swap) == CellState.DONE, "swapped: done");
        h.assertTrue(CellState.of(Blocks.STONE.defaultBlockState(), swap) == CellState.BLOCKED, "something else: blocked");
        Change fence = place(p, Blocks.OAK_FENCE.defaultBlockState());
        h.assertTrue(CellState.of(Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true), fence) == CellState.DONE, "connections ignored");
        h.succeed();
    }

    static Projection sample(GameTestHelper h, String name) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        ChangeSet set = ChangeSet.builder()
                .add(place(h.absolutePos(new BlockPos(1, Y, 1)), STAIRS_N))
                .group(List.of(place(h.absolutePos(new BlockPos(3, Y, 1)), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)),
                        place(h.absolutePos(new BlockPos(3, Y + 1, 1)), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))))
                .build();
        return Projection.of(set, "renovator.projection.label.blueprint", name, Level.OVERWORLD);
    }

    @GameTest(template = T)
    public static void storeRoundTrip(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID player = UUID.randomUUID();
        Projection a = sample(h, "house"), b = sample(h, "tower");
        ProjectionStore.add(server, player, a);
        ProjectionStore.add(server, player, b);
        ProjectionStore.clearCacheForTests(server);
        List<Projection> back = ProjectionStore.list(server, player);
        h.assertTrue(back.size() == 2, "two projections after reload, got " + back.size());
        Projection r = back.get(0);
        h.assertTrue(r.id().equals(a.id()) && r.labelArg().equals("house"), "first is the house: " + r.labelArg());
        h.assertTrue(r.set().groups().size() == 2 && r.set().groups().get(1).size() == 2, "door group kept: " + r.set().groups());
        h.assertTrue(r.set().changes().get(0).newState() == STAIRS_N, "target state kept");
        h.assertTrue(r.dimension() == Level.OVERWORLD, "dimension kept");
        ProjectionStore.remove(server, player, a.id());
        h.assertTrue(ProjectionStore.list(server, player).size() == 1, "removed");
        ProjectionStore.remove(server, player, b.id());
        h.succeed();
    }

    @GameTest(template = T)
    public static void storeKeepsEight(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID player = UUID.randomUUID();
        Projection first = sample(h, "p0");
        ProjectionStore.add(server, player, first);
        for (int i = 1; i <= 8; i++) ProjectionStore.add(server, player, sample(h, "p" + i));
        List<Projection> all = ProjectionStore.list(server, player);
        h.assertTrue(all.size() == ProjectionStore.MAX, "at most " + ProjectionStore.MAX + ", got " + all.size());
        h.assertTrue(ProjectionStore.get(server, player, first.id()).isEmpty(), "the oldest was dropped");
        for (Projection p : all) ProjectionStore.remove(server, player, p.id());
        h.succeed();
    }
}
