package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.core.Applier;
import com.renovator.core.CreativeGate;
import com.renovator.core.ResolvedSelection;
import com.renovator.core.Selection;
import com.renovator.family.Shape;
import com.renovator.mix.MixPalette;
import com.renovator.mix.Mixes;
import com.renovator.swap.SwapMapping;
import com.renovator.swap.SwapPlanner;
import com.renovator.swap.Target;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class SwapTests {
    private static final String T = RenovatorGameTests.T;
    static final int Y = RenovatorGameTests.FLOOR + 1;

    static ResourceLocation mc(String p) {
        return ResourceLocation.withDefaultNamespace(p);
    }

    /** An oak "hut" of parts inside x 1..10, z 1..6, plus a natural tree at (12, Y, 12) and a chest. */
    static void oakParts(GameTestHelper h) {
        for (int x = 1; x <= 4; x++) h.setBlock(new BlockPos(x, Y, 1), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(1, Y, 2), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
        h.setBlock(new BlockPos(2, Y, 2), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        h.setBlock(new BlockPos(4, Y, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(4, Y + 1, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x = 1; x <= 3; x++) {
            h.setBlock(new BlockPos(x, Y, 5), Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(FenceBlock.EAST, x < 3).setValue(FenceBlock.WEST, x > 1));
        }
        h.setBlock(new BlockPos(6, Y, 1), Blocks.OAK_TRAPDOOR);
        h.setBlock(new BlockPos(7, Y, 1), Blocks.OAK_SIGN);
        if (h.getBlockEntity(new BlockPos(7, Y, 1)) instanceof SignBlockEntity sign) {
            sign.setText(new SignText().setMessage(0, Component.literal("Hallo")), true);
        }
        h.setBlock(new BlockPos(8, Y, 1), Blocks.CHEST);
        for (int y = Y; y < Y + 3; y++) h.setBlock(new BlockPos(12, y, 12), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(12, Y + 3, 12), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
    }

    static ResolvedSelection all(GameTestHelper h) {
        return new Selection.Box(h.absolutePos(new BlockPos(0, Y, 0)), h.absolutePos(new BlockPos(14, Y + 4, 14))).resolve(h.getLevel(), 100_000);
    }

    static SwapPlanner.Result plan(GameTestHelper h, SwapMapping m, Map<String, MixPalette> mixes, long seed) {
        return SwapPlanner.plan(h.getLevel(), all(h), m, mixes, seed, new SwapPlanner.Options(false, false));
    }

    static void present(GameTestHelper h, Block b, int x, int y, int z) {
        h.assertBlockPresent(b, new BlockPos(x, y, z));
    }

    @GameTest(template = T)
    public static void oakToSpruce(GameTestHelper h) {
        oakParts(h);
        SwapPlanner.Result r = plan(h, SwapMapping.EMPTY.with(mc("oak"), Target.family(mc("spruce"))), Map.of(), 1);
        Applier.applyNow(h.getLevel(), r.changes(), CreativeGate.INSTANCE);
        present(h, Blocks.SPRUCE_PLANKS, 3, Y, 1);
        BlockState stairs = h.getBlockState(new BlockPos(1, Y, 2));
        h.assertTrue(stairs.is(Blocks.SPRUCE_STAIRS) && stairs.getValue(StairBlock.FACING) == Direction.EAST && stairs.getValue(StairBlock.HALF) == Half.TOP, "stairs " + stairs);
        h.assertTrue(h.getBlockState(new BlockPos(2, Y, 2)).getValue(SlabBlock.TYPE) == SlabType.DOUBLE, "double slab");
        present(h, Blocks.SPRUCE_DOOR, 4, Y, 3);
        present(h, Blocks.SPRUCE_DOOR, 4, Y + 1, 3);
        BlockState fence = h.getBlockState(new BlockPos(2, Y, 5));
        h.assertTrue(fence.is(Blocks.SPRUCE_FENCE) && fence.getValue(FenceBlock.EAST) && fence.getValue(FenceBlock.WEST), "fence keeps connections " + fence);
        present(h, Blocks.SPRUCE_TRAPDOOR, 6, Y, 1);
        present(h, Blocks.SPRUCE_SIGN, 7, Y, 1);
        SignBlockEntity sign = h.getBlockEntity(new BlockPos(7, Y, 1));
        h.assertTrue(sign.getFrontText().getMessage(0, false).getString().equals("Hallo"), "sign text kept");
        present(h, Blocks.CHEST, 8, Y, 1);
        present(h, Blocks.OAK_LOG, 12, Y, 12);
        h.assertTrue(r.kept().isEmpty(), "nothing kept for oak → spruce");
        h.succeed();
    }

    @GameTest(template = T)
    public static void shapeOverride(GameTestHelper h) {
        oakParts(h);
        SwapMapping m = SwapMapping.EMPTY.with(mc("oak"), Target.family(mc("spruce"))).withShape(mc("oak"), Shape.SLAB, Target.keep());
        Applier.applyNow(h.getLevel(), plan(h, m, Map.of(), 1).changes(), CreativeGate.INSTANCE);
        present(h, Blocks.OAK_SLAB, 2, Y, 2);
        present(h, Blocks.SPRUCE_PLANKS, 1, Y, 1);
        h.succeed();
    }

    @GameTest(template = T)
    public static void fallbackAndKept(GameTestHelper h) {
        oakParts(h);
        SwapPlanner.Result r = plan(h, SwapMapping.EMPTY.with(mc("oak"), Target.family(mc("stone_bricks"))), Map.of(), 1);
        Applier.applyNow(h.getLevel(), r.changes(), CreativeGate.INSTANCE);
        present(h, Blocks.STONE_BRICKS, 1, Y, 1);
        present(h, Blocks.STONE_BRICK_STAIRS, 1, Y, 2);
        present(h, Blocks.STONE_BRICK_WALL, 2, Y, 5);
        present(h, Blocks.OAK_DOOR, 4, Y, 3);
        present(h, Blocks.OAK_TRAPDOOR, 6, Y, 1);
        SwapPlanner.Kept door = r.kept().stream().filter(k -> k.shape() == Shape.DOOR).findFirst().orElse(null);
        h.assertTrue(door != null && door.count() == 1, "one door kept: " + r.kept());
        h.succeed();
    }

    @GameTest(template = T)
    public static void mixAndDeterminism(GameTestHelper h) {
        for (int x = 1; x <= 8; x++)
            for (int y = 0; y < 6; y++) h.setBlock(new BlockPos(x, Y + y, 3), Blocks.STONE_BRICKS);
        for (int x = 1; x <= 8; x++) h.setBlock(new BlockPos(x, Y, 5), Blocks.STONE_BRICK_STAIRS);
        Map<String, MixPalette> mixes = Mixes.all();
        SwapMapping m = SwapMapping.EMPTY.with(mc("stone_bricks"), Target.mix("renovator:old_mossy"));
        SwapPlanner.Result r1 = plan(h, m, mixes, 7), r2 = plan(h, m, mixes, 7);
        h.assertTrue(r1.changes().equals(r2.changes()), "same seed → same plan");
        Applier.applyNow(h.getLevel(), r1.changes(), CreativeGate.INSTANCE);
        Set<Block> wall = new HashSet<>();
        for (int x = 1; x <= 8; x++)
            for (int y = 0; y < 6; y++) wall.add(h.getBlockState(new BlockPos(x, Y + y, 3)).getBlock());
        Set<Block> allowed = Set.of(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        h.assertTrue(wall.size() >= 2 && allowed.containsAll(wall), "mixed wall " + wall);
        for (int x = 1; x <= 8; x++) {
            Block b = h.getBlockState(new BlockPos(x, Y, 5)).getBlock();
            h.assertTrue(b == Blocks.STONE_BRICK_STAIRS || b == Blocks.MOSSY_STONE_BRICK_STAIRS, "stairs only from families with stairs: " + b);
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void census(GameTestHelper h) {
        oakParts(h);
        var census = SwapPlanner.census(h.getLevel(), all(h), false);
        var oak = census.stream().filter(c -> c.family().id().equals(mc("oak"))).findFirst().orElseThrow();
        // 4 planks + stairs + slab + door (once) + 3 fences + trapdoor + sign = 12; the tree is nature, the chest protected
        h.assertTrue(oak.count() == 12, "oak count 12, was " + oak.count() + " " + oak.shapes());
        h.assertTrue(oak.shapes().get(Shape.FENCE) == 3, "3 fences");
        h.assertTrue(census.get(0).family().id().equals(mc("oak")), "oak is the most frequent");
        h.succeed();
    }
}
