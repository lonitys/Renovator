package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.core.Applier;
import com.stilwerk.core.Change;
import com.stilwerk.core.CreativeGate;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import com.stilwerk.detail.BuildingView;
import com.stilwerk.detail.DetailPlanner;
import com.stilwerk.detail.DetailTypes.Choice;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.detail.DetailTypes.Variant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class DetailTests {
    private static final String T = StilwerkGameTests.T;
    static final int Y0 = StilwerkGameTests.FLOOR + 1;

    /** A small house: stone brick base ring, plank walls (3 high), plank roof, door + 2 windows south, eave stairs north. */
    static void house(GameTestHelper h, Block planks, Block stairs, Block door) {
        for (int x = 2; x <= 8; x++)
            for (int z = 2; z <= 7; z++) {
                boolean edge = x == 2 || x == 8 || z == 2 || z == 7;
                if (edge) h.setBlock(new BlockPos(x, Y0, z), Blocks.STONE_BRICKS);
                for (int y = 1; y <= 3; y++) if (edge) h.setBlock(new BlockPos(x, Y0 + y, z), planks);
                h.setBlock(new BlockPos(x, Y0 + 4, z), planks);
            }
        BlockState d = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        h.setBlock(new BlockPos(5, Y0 + 1, 7), d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(5, Y0 + 2, 7), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        h.setBlock(new BlockPos(3, Y0 + 2, 7), Blocks.GLASS_PANE);
        h.setBlock(new BlockPos(7, Y0 + 2, 7), Blocks.GLASS_PANE);
        for (int x = 2; x <= 8; x++) h.setBlock(new BlockPos(x, Y0 + 4, 1), stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
    }

    static ResolvedSelection box(GameTestHelper h) {
        return new Selection.Box(h.absolutePos(new BlockPos(0, Y0, 0)), h.absolutePos(new BlockPos(10, Y0 + 7, 9))).resolve(h.getLevel(), 100_000);
    }

    static List<Suggestion> find(GameTestHelper h) {
        return DetailPlanner.find(h.getLevel(), box(h), 5);
    }

    @GameTest(template = T)
    public static void buildingView(GameTestHelper h) {
        house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        BuildingView v = new BuildingView(h.getLevel(), box(h));
        h.assertTrue(v.outside(h.absolutePos(new BlockPos(5, Y0 + 2, 8))), "in front of the south wall is outside");
        h.assertFalse(v.outside(h.absolutePos(new BlockPos(5, Y0 + 2, 4))), "inside the house is not outside");
        h.assertTrue(v.wood().id().getPath().equals("oak"), "dominant wood oak, was " + v.wood().id());
        h.assertTrue(v.stone().id().getPath().equals("stone_bricks"), "dominant stone stone_bricks, was " + v.stone().id());
        h.succeed();
    }

    @GameTest(template = T)
    public static void houseTriggersRules(GameTestHelper h) {
        house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        List<Suggestion> all = find(h);
        Set<String> rules = all.stream().map(Suggestion::rule).collect(Collectors.toSet());
        for (String r : List.of("eaves", "window", "pillars", "plinth", "corner", "entrance", "plain")) {
            h.assertTrue(rules.contains(r), "rule " + r + " found; got " + rules);
        }
        h.assertTrue(all.stream().filter(s -> s.rule().equals("window")).count() == 2, "two windows");
        // every change either fills a free cell or swaps a built full block
        for (Suggestion s : all)
            for (Variant v : s.variants())
                for (Change c : v.changes().changes()) {
                    BlockState old = c.oldState();
                    boolean ok = old.isAir() || old.canBeReplaced() || old.isCollisionShapeFullBlock(h.getLevel(), c.pos());
                    h.assertTrue(ok, s.rule() + "/" + v.key() + " touches " + old + " at " + c.pos());
                }
        List<String> again = find(h).stream().map(Suggestion::key).toList();
        h.assertTrue(again.equals(all.stream().map(Suggestion::key).toList()), "deterministic");
        h.succeed();
    }

    @GameTest(template = T)
    public static void materialsFollowTheBuilding(GameTestHelper h) {
        house(h, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_DOOR);
        Suggestion window = find(h).stream().filter(s -> s.rule().equals("window")).findFirst().orElseThrow();
        Variant shutters = window.variants().get(0);
        h.assertTrue(shutters.key().endsWith("shutters"), "first variant shutters: " + shutters.key());
        for (Change c : shutters.changes().changes()) {
            h.assertTrue(c.newState().is(Blocks.SPRUCE_TRAPDOOR) && c.newState().getValue(TrapDoorBlock.OPEN), "spruce shutters: " + c.newState());
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void applyChoices(GameTestHelper h) {
        house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        List<Suggestion> all = find(h);
        Suggestion window = all.stream().filter(s -> s.rule().equals("window")).findFirst().orElseThrow();
        Suggestion entrance = all.stream().filter(s -> s.rule().equals("entrance")).findFirst().orElseThrow();
        var cs = DetailPlanner.combine(all, List.of(new Choice("window", window.anchor(), 0), new Choice("entrance", entrance.anchor(), 0),
                new Choice("nope", BlockPos.ZERO, 0)));
        Applier.applyNow(h.getLevel(), cs, CreativeGate.INSTANCE);
        h.assertBlockPresent(Blocks.OAK_TRAPDOOR, new BlockPos(2, Y0 + 2, 8));
        h.assertBlockPresent(Blocks.OAK_TRAPDOOR, new BlockPos(4, Y0 + 2, 8));
        // the entrance lanterns want the shutter cell next to the door: the whole entrance suggestion is skipped,
        // no lone fence posts are left behind
        h.assertBlockNotPresent(Blocks.OAK_FENCE, new BlockPos(4, Y0 + 1, 8));
        h.assertBlockNotPresent(Blocks.OAK_FENCE, new BlockPos(6, Y0 + 1, 8));
        h.succeed();
    }

    @GameTest(template = T)
    public static void staircaseRailings(GameTestHelper h) {
        for (int i = 0; i < 3; i++) {
            h.setBlock(new BlockPos(3 + i, Y0 + i, 5), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            for (int y = 0; y < i; y++) h.setBlock(new BlockPos(3 + i, Y0 + y, 5), Blocks.SPRUCE_PLANKS);
        }
        h.setBlock(new BlockPos(6, Y0 + 2, 5), Blocks.SPRUCE_PLANKS);
        List<Suggestion> all = DetailPlanner.find(h.getLevel(), box(h), 1);
        Suggestion st = all.stream().filter(s -> s.rule().equals("stairs")).findFirst().orElse(null);
        h.assertTrue(st != null && st.variants().size() == 3, "staircase found with 3 variants: " + all.stream().map(Suggestion::rule).toList());
        Applier.applyNow(h.getLevel(), st.variants().get(0).changes(), CreativeGate.INSTANCE);
        h.assertBlockPresent(Blocks.SPRUCE_FENCE, new BlockPos(3, Y0, 4));
        h.assertBlockPresent(Blocks.SPRUCE_FENCE, new BlockPos(5, Y0 + 2, 6));
        h.assertBlockPresent(Blocks.LANTERN, new BlockPos(3, Y0 + 1, 4));
        h.succeed();
    }

    @GameTest(template = T)
    public static void pillarsAvoidDoorsAndKeepPlinth(GameTestHelper h) {
        // a free-standing wall x 1..9: stone brick plinth, planks above, a door at x = 5
        for (int x = 1; x <= 9; x++) {
            h.setBlock(new BlockPos(x, Y0, 3), Blocks.STONE_BRICKS);
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, Y0 + y, 3), Blocks.OAK_PLANKS);
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        h.setBlock(new BlockPos(5, Y0 + 1, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(5, Y0 + 2, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        ResolvedSelection sel = new Selection.Box(h.absolutePos(new BlockPos(0, Y0, 2)), h.absolutePos(new BlockPos(10, Y0 + 4, 4))).resolve(h.getLevel(), 10_000);
        Suggestion pillars = DetailPlanner.find(h.getLevel(), sel, 1).stream().filter(s -> s.rule().equals("pillars")).findFirst().orElse(null);
        h.assertTrue(pillars != null, "pillars suggested for the wall");
        Applier.applyNow(h.getLevel(), pillars.variants().get(0).changes(), CreativeGate.INSTANCE);
        for (int y = 1; y <= 3; y++) {
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, Y0 + y, 3));
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(9, Y0 + y, 3));
        }
        h.assertBlockPresent(Blocks.OAK_DOOR, new BlockPos(5, Y0 + 1, 3));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, Y0 + 3, 3)); // no single log above the door
        boolean shifted = h.getBlockState(new BlockPos(4, Y0 + 2, 3)).is(Blocks.OAK_LOG) || h.getBlockState(new BlockPos(6, Y0 + 2, 3)).is(Blocks.OAK_LOG);
        h.assertTrue(shifted, "the middle pillar moved next to the door");
        for (int x = 1; x <= 9; x++) h.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(x, Y0, 3)); // plinth untouched
        h.succeed();
    }

    @GameTest(template = T)
    public static void entranceLanternsBothOrNone(GameTestHelper h) {
        house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        h.setBlock(new BlockPos(4, Y0 + 1, 8), Blocks.COBBLESTONE); // the west lantern post cell is taken
        Suggestion entrance = find(h).stream().filter(s -> s.rule().equals("entrance")).findFirst().orElse(null);
        h.assertTrue(entrance != null, "entrance suggested");
        for (var v : entrance.variants()) {
            h.assertFalse(v.key().endsWith("lanterns"), "no single lantern post: " + v.key());
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void roofIsNotAStaircase(GameTestHelper h) {
        // a gable roof: 10 wide rows of stairs rising from both sides, gables filled with planks
        for (int x = 1; x <= 10; x++)
            for (int step = 0; step < 4; step++) {
                h.setBlock(new BlockPos(x, Y0 + step, 2 + step), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                h.setBlock(new BlockPos(x, Y0 + step, 11 - step), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
        for (int step = 0; step < 3; step++)
            for (int z = 3 + step; z <= 10 - step; z++) {
                h.setBlock(new BlockPos(2, Y0 + step, z), Blocks.OAK_PLANKS);
                h.setBlock(new BlockPos(9, Y0 + step, z), Blocks.OAK_PLANKS);
            }
        // gaps in the lowest row (chains for hanging lanterns) must not turn the roof ends into staircases
        h.setBlock(new BlockPos(4, Y0, 2), Blocks.CHAIN);
        h.setBlock(new BlockPos(7, Y0, 2), Blocks.CHAIN);
        ResolvedSelection sel = new Selection.Box(h.absolutePos(new BlockPos(1, Y0, 1)), h.absolutePos(new BlockPos(10, Y0 + 4, 12))).resolve(h.getLevel(), 10_000);
        List<Suggestion> all = DetailPlanner.find(h.getLevel(), sel, 1);
        List<String> stairs = all.stream().filter(s -> s.rule().equals("stairs")).map(s -> s.anchor().subtract(h.absolutePos(BlockPos.ZERO)).toShortString()).toList();
        h.assertTrue(stairs.isEmpty(), "roof slopes are not staircases: " + stairs);
        h.succeed();
    }

    @GameTest(template = T)
    public static void nearClick(GameTestHelper h) {
        house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        List<Suggestion> near = DetailPlanner.near(h.getLevel(), h.absolutePos(new BlockPos(7, Y0 + 2, 7)), 5);
        h.assertTrue(near.stream().anyMatch(s -> s.rule().equals("window")), "window near the clicked pane: " + near.stream().map(Suggestion::rule).toList());
        h.assertFalse(near.stream().anyMatch(s -> s.rule().equals("eaves")), "the north eaves are too far away");
        h.succeed();
    }
}
