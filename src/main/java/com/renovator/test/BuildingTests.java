package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.core.ResolvedSelection;
import com.renovator.core.Selection;
import com.renovator.detail.DetailPlanner;
import com.renovator.detail.DetailTypes.Suggestion;
import com.renovator.detail.DetailTypes.Variant;
import com.renovator.core.Change;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Building detection in a small village: a house with a lamp post, a dirt path to a cobblestone road, a shed with a
 * stone path, a fenced field between them, a low garden wall and a tree. Only the clicked building may be selected.
 */
@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class BuildingTests {
    static final String V = "village";
    /** Relative y of the grass surface of the village template. */
    static final int G = 4;

    static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    static void door(GameTestHelper h, int x, int y, int z, Block door) {
        BlockState d = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        h.setBlock(at(x, y, z), d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(at(x, y + 1, z), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static void village(GameTestHelper h) {
        // house A: x 10..16, z 10..15, plinth, planks walls, flat roof, wooden floor in the ground
        for (int x = 10; x <= 16; x++)
            for (int z = 10; z <= 15; z++) {
                boolean edge = x == 10 || x == 16 || z == 10 || z == 15;
                if (edge) {
                    h.setBlock(at(x, G + 1, z), Blocks.STONE_BRICKS);
                    for (int y = G + 2; y <= G + 4; y++) h.setBlock(at(x, y, z), Blocks.OAK_PLANKS);
                } else {
                    h.setBlock(at(x, G, z), Blocks.SPRUCE_PLANKS);
                }
                h.setBlock(at(x, G + 5, z), Blocks.OAK_PLANKS);
            }
        door(h, 13, G + 1, 15, Blocks.OAK_DOOR);
        h.setBlock(at(11, G + 3, 15), Blocks.GLASS_PANE);
        h.setBlock(at(15, G + 3, 15), Blocks.GLASS_PANE);
        // lamp post next to the door
        h.setBlock(at(12, G + 1, 16), Blocks.OAK_FENCE);
        h.setBlock(at(12, G + 2, 16), Blocks.OAK_FENCE);
        h.setBlock(at(12, G + 3, 16), Blocks.LANTERN);
        // dirt path to the road, the road
        for (int z = 16; z <= 30; z++) h.setBlock(at(13, G, z), Blocks.DIRT_PATH);
        for (int x = 2; x <= 37; x++) h.setBlock(at(x, G, 31), Blocks.COBBLESTONE);
        // shed B: x 26..30, z 20..24, cobblestone, slab roof, stone brick path to the road
        for (int x = 26; x <= 30; x++)
            for (int z = 20; z <= 24; z++) {
                boolean edge = x == 26 || x == 30 || z == 20 || z == 24;
                if (edge) for (int y = G + 1; y <= G + 3; y++) h.setBlock(at(x, y, z), Blocks.COBBLESTONE);
                h.setBlock(at(x, G + 4, z), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
        door(h, 28, G + 1, 24, Blocks.SPRUCE_DOOR);
        for (int z = 25; z <= 30; z++) h.setBlock(at(28, G, z), Blocks.STONE_BRICKS);
        // fenced wheat field between A and B, fence attached to both
        for (int x = 19; x <= 23; x++)
            for (int z = 13; z <= 18; z++) {
                h.setBlock(at(x, G, z), Blocks.FARMLAND);
                h.setBlock(at(x, G + 1, z), Blocks.WHEAT);
            }
        for (int x = 18; x <= 24; x++) {
            h.setBlock(at(x, G + 1, 12), Blocks.OAK_FENCE);
            h.setBlock(at(x, G + 1, 19), Blocks.OAK_FENCE);
        }
        for (int z = 13; z <= 18; z++) {
            h.setBlock(at(18, G + 1, z), Blocks.OAK_FENCE);
            h.setBlock(at(24, G + 1, z), Blocks.OAK_FENCE);
        }
        h.setBlock(at(17, G + 1, 12), Blocks.OAK_FENCE);
        h.setBlock(at(25, G + 1, 19), Blocks.OAK_FENCE);
        h.setBlock(at(25, G + 1, 20), Blocks.OAK_FENCE);
        // low garden wall west of the house
        for (int x = 2; x <= 9; x++) {
            h.setBlock(at(x, G + 1, 14), Blocks.COBBLESTONE);
            h.setBlock(at(x, G + 2, 14), Blocks.COBBLESTONE);
        }
        // a tree close to the west wall
        for (int y = G + 1; y <= G + 5; y++) h.setBlock(at(7, y, 11), Blocks.OAK_LOG);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = G + 4; y <= G + 6; y++) {
                    BlockPos l = at(7 + dx, y, 11 + dz);
                    if (h.getBlockState(l).isAir()) h.setBlock(l, Blocks.OAK_LEAVES);
                }
    }

    static ResolvedSelection flood(GameTestHelper h, BlockPos start) {
        return new Selection.Flood(h.absolutePos(start), 48).resolve(h.getLevel(), 1_000_000);
    }

    static void has(GameTestHelper h, ResolvedSelection r, String what, BlockPos p) {
        h.assertTrue(r.contains(h.absolutePos(p)), what + " " + p + " selected (" + r.size() + " blocks)");
    }

    static void hasNot(GameTestHelper h, ResolvedSelection r, String what, BlockPos p) {
        h.assertFalse(r.contains(h.absolutePos(p)), what + " " + p + " must not be selected (" + r.size() + " blocks)");
    }

    @GameTest(template = V)
    public static void floodSelectsOnlyTheHouse(GameTestHelper h) {
        village(h);
        ResolvedSelection r = flood(h, at(10, G + 2, 12));
        has(h, r, "plinth", at(10, G + 1, 12));
        has(h, r, "roof", at(13, G + 5, 12));
        has(h, r, "floor", at(13, G, 12));
        has(h, r, "door", at(13, G + 1, 15));
        has(h, r, "window", at(11, G + 3, 15));
        has(h, r, "lamp post", at(12, G + 1, 16));
        has(h, r, "lantern", at(12, G + 3, 16));
        hasNot(h, r, "dirt path", at(13, G, 20));
        hasNot(h, r, "road", at(20, G, 31));
        hasNot(h, r, "shed", at(26, G + 2, 22));
        hasNot(h, r, "field fence", at(20, G + 1, 12));
        hasNot(h, r, "fence touching the house", at(17, G + 1, 12));
        hasNot(h, r, "farmland", at(20, G, 15));
        hasNot(h, r, "wheat", at(20, G + 1, 15));
        hasNot(h, r, "garden wall", at(5, G + 1, 14));
        hasNot(h, r, "garden wall at the house", at(9, G + 2, 14));
        hasNot(h, r, "tree", at(7, G + 2, 11));
        hasNot(h, r, "leaves", at(9, G + 4, 11));
        h.succeed();
    }

    @GameTest(template = V)
    public static void floodFromOtherParts(GameTestHelper h) {
        village(h);
        ResolvedSelection shed = flood(h, at(26, G + 2, 22));
        has(h, shed, "shed roof", at(28, G + 4, 22));
        hasNot(h, shed, "house", at(10, G + 2, 12));
        hasNot(h, shed, "shed path", at(28, G, 27));
        hasNot(h, shed, "road", at(30, G, 31));
        ResolvedSelection window = flood(h, at(11, G + 3, 15));
        has(h, window, "house from its window", at(13, G + 5, 12));
        ResolvedSelection road = flood(h, at(20, G, 31));
        has(h, road, "road", at(5, G, 31));
        has(h, road, "shed path joins the road", at(28, G, 27));
        hasNot(h, road, "shed", at(26, G + 2, 22));
        ResolvedSelection fence = flood(h, at(20, G + 1, 12));
        has(h, fence, "whole field fence", at(24, G + 1, 15));
        hasNot(h, fence, "house", at(16, G + 2, 12));
        hasNot(h, fence, "farmland", at(20, G, 15));
        h.succeed();
    }

    /** Cells of the road, the paths, the garden wall and the field. */
    static boolean foreign(BlockPos r) {
        int x = r.getX(), y = r.getY(), z = r.getZ();
        boolean road = y == G && (z == 31 || (x == 28 && z >= 25 && z <= 30) || (x == 13 && z >= 16));
        boolean garden = z == 14 && x <= 9 && y >= G + 1 && y <= G + 2;
        boolean fenceLine = (z == 12 || z == 19) && x >= 17 && x <= 25 || (x == 18 || x == 24) && z >= 12 && z <= 19 || x == 25 && z == 20;
        boolean fence = fenceLine && y == G + 1;
        boolean field = x >= 19 && x <= 23 && z >= 13 && z <= 18 && (y == G || y == G + 1);
        return road || garden || fence || field;
    }

    static void noForeign(GameTestHelper h, List<Suggestion> found, String mode) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        for (Suggestion s : found)
            for (Variant v : s.variants())
                for (Change c : v.changes().changes()) {
                    BlockPos r = c.pos().subtract(origin);
                    h.assertFalse(foreign(r), mode + ": " + s.rule() + "/" + v.key() + " changes " + r + " (road, garden wall or field)");
                }
    }

    @GameTest(template = V)
    public static void targetedDetailsStayOnTheHouse(GameTestHelper h) {
        village(h);
        BlockPos min = h.absolutePos(at(10, G, 10)), max = h.absolutePos(at(16, G + 5, 15));
        BoundingBox house = BoundingBox.fromCorners(min, max).inflatedBy(3);
        for (BlockPos click : List.of(at(10, G + 2, 13), at(13, G + 3, 15), at(16, G + 1, 12))) {
            List<Suggestion> near = DetailPlanner.near(h.getLevel(), h.absolutePos(click), 1);
            h.assertFalse(near.isEmpty(), "suggestions at " + click);
            noForeign(h, near, "near " + click);
            for (Suggestion s : near)
                for (Variant v : s.variants())
                    for (Change c : v.changes().changes())
                        h.assertTrue(house.isInside(c.pos()), "near " + click + ": " + s.rule() + "/" + v.key() + " changes " + c.pos() + ", away from the house");
        }
        h.succeed();
    }

    @GameTest(template = V)
    public static void boxDetailsSkipRoadsAndGardens(GameTestHelper h) {
        village(h);
        ResolvedSelection all = new Selection.Box(h.absolutePos(at(0, G, 0)), h.absolutePos(at(39, G + 8, 39))).resolve(h.getLevel(), 1_000_000);
        List<Suggestion> found = DetailPlanner.find(h.getLevel(), all, 1);
        h.assertTrue(found.stream().anyMatch(s -> s.rule().equals("entrance")), "the doors still get suggestions");
        noForeign(h, found, "box");
        h.succeed();
    }
}
