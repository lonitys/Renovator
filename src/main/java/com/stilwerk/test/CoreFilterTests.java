package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.core.Groups;
import com.stilwerk.core.NatureFilter;
import com.stilwerk.core.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class CoreFilterTests {
    private static final String T = StilwerkGameTests.T;

    private static boolean natural(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return new NatureFilter(h.getLevel()).isNatural(abs, h.getLevel().getBlockState(abs));
    }

    @GameTest(template = T)
    public static void naturalTreeLogIsNature(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(4, y, 4), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(4, 5, 4), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        h.assertTrue(natural(h, new BlockPos(4, 2, 4)), "bottom log of a tree should be natural");
        h.assertTrue(natural(h, new BlockPos(4, 5, 4)), "natural leaves should be natural");
        h.succeed();
    }

    @GameTest(template = T)
    public static void builtLogsAreNotNature(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(2, y, 2), Blocks.OAK_LOG);
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(8, y, 8), Blocks.SPRUCE_LOG);
        h.setBlock(new BlockPos(8, 5, 8), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        h.assertFalse(natural(h, new BlockPos(2, 3, 2)), "a bare log pillar is built");
        h.assertFalse(natural(h, new BlockPos(8, 2, 8)), "logs under placed leaves are built");
        h.assertFalse(natural(h, new BlockPos(8, 5, 8)), "placed leaves are built");
        h.succeed();
    }

    @GameTest(template = T)
    public static void terrainAndBuiltBlocks(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 2, 1), Blocks.GRASS_BLOCK);
        h.setBlock(new BlockPos(2, 2, 1), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(3, 2, 1), Blocks.COBBLESTONE);
        h.assertTrue(natural(h, new BlockPos(1, 2, 1)), "grass is natural");
        h.assertTrue(natural(h, new BlockPos(1, 1, 1)), "the stone floor is natural: " + h.getBlockState(new BlockPos(1, 1, 1)));
        h.assertFalse(natural(h, new BlockPos(2, 2, 1)), "planks are built");
        h.assertFalse(natural(h, new BlockPos(3, 2, 1)), "cobblestone is built");
        h.succeed();
    }

    @GameTest(template = T)
    public static void protectedBlocks(GameTestHelper h) {
        BlockPos chest = new BlockPos(1, 2, 1), bedrock = new BlockPos(2, 2, 1), sign = new BlockPos(3, 2, 1), planks = new BlockPos(4, 2, 1);
        h.setBlock(chest, Blocks.CHEST);
        h.setBlock(bedrock, Blocks.BEDROCK);
        h.setBlock(sign, Blocks.OAK_SIGN);
        h.setBlock(planks, Blocks.OAK_PLANKS);
        h.assertTrue(isProtected(h, chest), "chests are protected");
        h.assertTrue(isProtected(h, bedrock), "bedrock is protected");
        h.assertFalse(isProtected(h, sign), "signs are not protected");
        h.assertFalse(isProtected(h, planks), "planks are not protected");
        h.succeed();
    }

    private static boolean isProtected(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return Protection.isProtected(h.getLevel(), abs, h.getLevel().getBlockState(abs));
    }

    @GameTest(template = T)
    public static void doorAndBedPartners(GameTestHelper h) {
        BlockPos lower = new BlockPos(3, 2, 3);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        h.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState lowerState = h.getBlockState(lower), upperState = h.getBlockState(lower.above());
        h.assertTrue(Groups.partner(lower, lowerState).orElseThrow().equals(lower.above()), "lower partner is above");
        h.assertTrue(Groups.partner(lower.above(), upperState).orElseThrow().equals(lower), "upper partner is below");
        h.assertTrue(Groups.isPrimary(lowerState), "lower half is primary");
        h.assertFalse(Groups.isPrimary(upperState), "upper half is secondary");
        h.assertTrue(Groups.partner(lower, Blocks.OAK_PLANKS.defaultBlockState()).isEmpty(), "planks have no partner");
        h.succeed();
    }

    @GameTest(template = T)
    public static void legacyFoldersAreMoved(GameTestHelper h) throws java.io.IOException {
        java.nio.file.Path base = java.nio.file.Files.createTempDirectory("stilwerk_folders");
        java.nio.file.Path old = java.nio.file.Files.createDirectories(base.resolve("vorlagen"));
        java.nio.file.Files.writeString(old.resolve("mine.json"), "{}");
        java.nio.file.Path dir = com.stilwerk.config.Folders.resolve(base, "templates", "vorlagen");
        h.assertTrue(dir.equals(base.resolve("templates")), "English folder used: " + dir);
        h.assertTrue(java.nio.file.Files.exists(dir.resolve("mine.json")), "old files moved along");
        h.assertFalse(java.nio.file.Files.exists(old), "German folder gone");
        h.assertTrue(com.stilwerk.config.Folders.resolve(base, "mixes", "mischungen").equals(base.resolve("mixes")), "no old folder: plain path");
        h.succeed();
    }
}
