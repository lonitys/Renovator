package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class SelectionTests {
    private static final String T = StilwerkGameTests.T;

    @GameTest(template = T)
    public static void boxResolve(GameTestHelper h) {
        ResolvedSelection r = new Selection.Box(new BlockPos(1, 1, 1), new BlockPos(3, 2, 4)).resolve(h.getLevel(), 1000);
        h.assertTrue(r.size() == 24, "box size 24, was " + r.size());
        h.assertTrue(r.contains(new BlockPos(2, 2, 3)), "contains inner point");
        h.assertFalse(r.contains(new BlockPos(4, 1, 1)), "excludes outer point");
        int[] count = {0};
        r.forEach(p -> count[0]++);
        h.assertTrue(count[0] == 24, "iterates 24");
        ResolvedSelection big = new Selection.Box(new BlockPos(0, 0, 0), new BlockPos(99, 99, 99)).resolve(h.getLevel(), 1000);
        h.assertTrue(big.truncated() && big.isEmpty(), "oversized box is truncated and empty");
        h.succeed();
    }

    /** A 3x3x3 plank cube on the floor, grass next to it. */
    static void hut(GameTestHelper h, BlockPos min) {
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) h.setBlock(min.offset(x, y, z), Blocks.OAK_PLANKS);
        h.setBlock(min.offset(-1, 0, 0), Blocks.GRASS_BLOCK);
        h.setBlock(min.offset(3, 0, 0), Blocks.DIRT);
    }

    @GameTest(template = T)
    public static void floodStopsAtNature(GameTestHelper h) {
        BlockPos min = new BlockPos(5, StilwerkGameTests.FLOOR + 1, 5);
        hut(h, min);
        ResolvedSelection r = new Selection.Flood(h.absolutePos(min), 10).resolve(h.getLevel(), 10_000);
        h.assertTrue(r.size() == 27, "hut has 27 blocks, flood found " + r.size());
        h.succeed();
    }

    @GameTest(template = T)
    public static void floodRadiusAndLimit(GameTestHelper h) {
        int y = StilwerkGameTests.FLOOR + 1;
        for (int x = 0; x < 10; x++) h.setBlock(new BlockPos(x + 2, y, 2), Blocks.OAK_PLANKS);
        ResolvedSelection r = new Selection.Flood(h.absolutePos(new BlockPos(2, y, 2)), 3).resolve(h.getLevel(), 10_000);
        h.assertTrue(r.size() == 4, "radius 3 from the line start selects 4, was " + r.size());
        ResolvedSelection limited = new Selection.Flood(h.absolutePos(new BlockPos(2, y, 2)), 20).resolve(h.getLevel(), 5);
        h.assertTrue(limited.truncated(), "limit 5 truncates a 10 block line");
        ResolvedSelection none = new Selection.Flood(h.absolutePos(new BlockPos(2, StilwerkGameTests.FLOOR, 2)), 5).resolve(h.getLevel(), 100);
        h.assertTrue(none.isEmpty() && !none.truncated(), "starting on natural stone selects nothing");
        h.succeed();
    }
}
