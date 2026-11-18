package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.net.BuildMode;
import com.renovator.net.ProjectionPayloads.Action;
import com.renovator.net.ProjectionPayloads.Build;
import com.renovator.net.ProjectionPayloads.Place;
import com.renovator.net.ProjectionPayloads.ProjAction;
import com.renovator.net.ProjectionPayloads.Scope;
import com.renovator.net.ServerOps;
import com.renovator.net.SmallPayloads;
import com.renovator.net.SwapRequest;
import com.renovator.projection.Projection;
import com.renovator.projection.ProjectionOps;
import com.renovator.projection.ProjectionStore;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class ProjectionOpsTests {
    private static final String T = RenovatorGameTests.T;
    static final int Y = RenovatorGameTests.FLOOR + 1;

    static Change place(GameTestHelper h, int x, int y, int z, BlockState target) {
        return new Change(h.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), target, null, false);
    }

    static Projection create(GameTestHelper h, FakePlayer p, ChangeSet set) {
        return ProjectionOps.create(p, h.getLevel(), set, "renovator.projection.label.blueprint", "test");
    }

    static void cleanup(GameTestHelper h, FakePlayer p) {
        for (Projection pr : ProjectionStore.list(h.getLevel().getServer(), p.getUUID())) ProjectionStore.remove(h.getLevel().getServer(), p.getUUID(), pr.id());
    }

    @GameTest(template = T)
    public static void creativeShiftKeepsProjection(GameTestHelper h) {
        SwapTests.oakParts(h);
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        ServerOps.swap(p, new SwapRequest(1, ServerOpsTests.box(h), ServerOpsTests.OAK_TO_SPRUCE, List.of(), 3, false, BuildMode.PROJECTION));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, Y, 1));
        List<Projection> list = ProjectionStore.list(h.getLevel().getServer(), p.getUUID());
        h.assertTrue(list.size() == 1 && list.get(0).cellCount() > 5, "one projection with the swap: " + list);
        h.assertTrue(list.get(0).labelKey().equals("renovator.projection.label.swap"), "labelled as swap");
        cleanup(h, p);
        h.succeed();
    }

    @GameTest(template = T)
    public static void survivalAlwaysAsks(GameTestHelper h) {
        SwapTests.oakParts(h);
        FakePlayer p = TestPlayers.survival(h);
        TestPlayers.give(p, Items.SPRUCE_PLANKS, 64);
        TestPlayers.give(p, Items.SPRUCE_STAIRS, 64);
        TestPlayers.give(p, Items.SPRUCE_DOOR, 8);
        TestPlayers.give(p, Items.SPRUCE_SLAB, 64);
        TestPlayers.give(p, Items.SPRUCE_FENCE, 64);
        ServerOps.swap(p, new SwapRequest(4, ServerOpsTests.box(h), ServerOpsTests.OAK_TO_SPRUCE, List.of(), 3, false, BuildMode.ASK));
        h.assertTrue(ServerOps.pendingForTests().containsKey(p.getUUID()), "dialog pending even with all materials");
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, Y, 1));
        ServerOps.confirm(p, new SmallPayloads.ConfirmRequest(4, SmallPayloads.ConfirmAction.PROJECT));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, Y, 1));
        h.assertTrue(ProjectionStore.list(h.getLevel().getServer(), p.getUUID()).size() == 1, "kept as projection");
        cleanup(h, p);
        h.succeed();
    }

    @GameTest(template = T)
    public static void placeByHand(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        Projection pr = create(h, p, ChangeSet.builder().add(place(h, 7, Y, 8, stairs)).add(place(h, 9, Y, 8, Blocks.OAK_PLANKS.defaultBlockState())).build());
        BlockPos stairsAt = h.absolutePos(new BlockPos(7, Y, 8)), planksAt = h.absolutePos(new BlockPos(9, Y, 8));
        // wrong item: nothing happens
        TestPlayers.give(p, Items.COBBLESTONE, 4);
        ProjectionOps.place(p, new Place(pr.id(), planksAt));
        h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(9, Y, 8));
        h.assertTrue("renovator.projection.need".equals(ServerOps.lastMessageForTests(p.getUUID())), "told what is needed");
        // the right item: exact state, item used
        TestPlayers.give(p, Items.OAK_STAIRS, 1);
        ProjectionOps.place(p, new Place(pr.id(), stairsAt));
        h.assertTrue(h.getBlockState(new BlockPos(7, Y, 8)).equals(stairs), "stairs facing east: " + h.getBlockState(new BlockPos(7, Y, 8)));
        h.assertTrue(TestPlayers.count(p, Items.OAK_STAIRS) == 0, "stairs used");
        h.assertTrue(ProjectionStore.get(h.getLevel().getServer(), p.getUUID(), pr.id()).isPresent(), "planks still open: projection stays");
        // too far away
        TestPlayers.give(p, Items.OAK_PLANKS, 1);
        p.moveTo(p.getX() + 40, p.getY(), p.getZ());
        ProjectionOps.place(p, new Place(pr.id(), planksAt));
        h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(9, Y, 8));
        p.moveTo(p.getX() - 40, p.getY(), p.getZ());
        // last cell: projection finished and removed
        ProjectionOps.place(p, new Place(pr.id(), planksAt));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(9, Y, 8));
        h.assertTrue(ProjectionStore.get(h.getLevel().getServer(), p.getUUID(), pr.id()).isEmpty(), "finished projection removed");
        cleanup(h, p);
        h.succeed();
    }

    @GameTest(template = T)
    public static void placeDoorAsOneGroup(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        Projection pr = create(h, p, ChangeSet.builder().group(List.of(
                place(h, 7, Y, 8, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)),
                place(h, 7, Y + 1, 8, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)))).build());
        TestPlayers.give(p, Items.OAK_DOOR, 1);
        ProjectionOps.place(p, new Place(pr.id(), h.absolutePos(new BlockPos(7, Y + 1, 8))));
        h.assertBlockPresent(Blocks.OAK_DOOR, new BlockPos(7, Y, 8));
        h.assertBlockPresent(Blocks.OAK_DOOR, new BlockPos(7, Y + 1, 8));
        h.assertTrue(TestPlayers.count(p, Items.OAK_DOOR) == 0, "one door item for both halves");
        cleanup(h, p);
        h.succeed();
    }

    @GameTest(template = T)
    public static void swapByHandGivesOldBlock(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        h.setBlock(new BlockPos(7, Y, 8), Blocks.OAK_PLANKS);
        Change swap = new Change(h.absolutePos(new BlockPos(7, Y, 8)), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), null, false);
        Projection pr = create(h, p, ChangeSet.builder().add(swap).build());
        TestPlayers.give(p, Items.SPRUCE_PLANKS, 1);
        ProjectionOps.place(p, new Place(pr.id(), swap.pos()));
        h.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(7, Y, 8));
        h.assertTrue(TestPlayers.count(p, Items.OAK_PLANKS) == 1 && TestPlayers.count(p, Items.SPRUCE_PLANKS) == 0, "oak plank given back");
        cleanup(h, p);
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void autoBuildLayerAsFarAsItGoes(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        ChangeSet.Builder b = ChangeSet.builder();
        for (int x = 2; x <= 4; x++) b.add(place(h, x, Y, 3, Blocks.OAK_PLANKS.defaultBlockState()));
        for (int x = 2; x <= 4; x++) b.add(place(h, x, Y + 1, 3, Blocks.OAK_PLANKS.defaultBlockState()));
        Projection pr = create(h, p, b.build());
        TestPlayers.give(p, Items.OAK_PLANKS, 2);
        ProjectionOps.build(p, new Build(pr.id(), Scope.LAYER, h.absolutePos(new BlockPos(0, Y, 0)).getY()));
        h.succeedWhen(() -> {
            int built = 0;
            for (int x = 2; x <= 4; x++) if (h.getBlockState(new BlockPos(x, Y, 3)).is(Blocks.OAK_PLANKS)) built++;
            h.assertTrue(built == 2, "two of three planks built in the layer, got " + built);
            for (int x = 2; x <= 4; x++) h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(x, Y + 1, 3));
            h.assertTrue(ProjectionStore.get(h.getLevel().getServer(), p.getUUID(), pr.id()).isPresent(), "the rest stays a projection");
            cleanup(h, p);
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void autoBuildAllInCreative(GameTestHelper h) {
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        ChangeSet.Builder b = ChangeSet.builder();
        for (int y = 0; y < 3; y++) b.add(place(h, 3, Y + y, 3, Blocks.STONE_BRICKS.defaultBlockState()));
        Projection pr = create(h, p, b.build());
        ProjectionOps.build(p, new Build(pr.id(), Scope.ALL, 0));
        h.succeedWhen(() -> {
            for (int y = 0; y < 3; y++) h.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(3, Y + y, 3));
            h.assertTrue(ProjectionStore.get(h.getLevel().getServer(), p.getUUID(), pr.id()).isEmpty(), "finished and removed");
        });
    }

    @GameTest(template = T)
    public static void doneAndDelete(GameTestHelper h) {
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        Projection done = create(h, p, ChangeSet.builder().add(place(h, 5, Y, 5, Blocks.GLASS.defaultBlockState())).build());
        Projection other = create(h, p, ChangeSet.builder().add(place(h, 6, Y, 5, Blocks.GLASS.defaultBlockState())).build());
        h.setBlock(new BlockPos(5, Y, 5), Blocks.GLASS); // built the normal way
        ProjectionOps.action(p, new ProjAction(done.id(), Action.REMOVE_IF_DONE));
        h.assertTrue(ProjectionStore.get(h.getLevel().getServer(), p.getUUID(), done.id()).isEmpty(), "built by hand: removed");
        ProjectionOps.action(p, new ProjAction(other.id(), Action.REMOVE_IF_DONE));
        h.assertTrue(ProjectionStore.get(h.getLevel().getServer(), p.getUUID(), other.id()).isPresent(), "not done: kept");
        ProjectionOps.action(p, new ProjAction(other.id(), Action.DELETE));
        h.assertTrue(ProjectionStore.list(h.getLevel().getServer(), p.getUUID()).isEmpty(), "deleted");
        h.succeed();
    }
}
