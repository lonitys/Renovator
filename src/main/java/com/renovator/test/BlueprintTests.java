package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.blueprint.Blueprint;
import com.renovator.blueprint.BlueprintIO;
import com.renovator.blueprint.BlueprintMeta;
import com.renovator.blueprint.Transform;
import com.renovator.core.ResolvedSelection;
import com.renovator.core.Selection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class BlueprintTests {
    private static final String T = RenovatorGameTests.T;
    private static final int Y = RenovatorGameTests.FLOOR + 1;

    /** 3x2x2 sample at (2,Y,2): planks, a north-facing stair, a sign with text, air gaps. */
    static Blueprint sample(GameTestHelper h) {
        h.setBlock(new BlockPos(2, Y, 2), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(3, Y, 2), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(4, Y, 3), Blocks.OAK_SIGN);
        if (h.getBlockEntity(new BlockPos(4, Y, 3)) instanceof SignBlockEntity sign) {
            sign.setText(new SignText().setMessage(0, Component.literal("Bauplan")), true);
        }
        h.setBlock(new BlockPos(2, Y + 1, 2), Blocks.STONE_BRICKS);
        ResolvedSelection sel = new Selection.Box(h.absolutePos(new BlockPos(2, Y, 2)), h.absolutePos(new BlockPos(4, Y + 1, 3)))
                .resolve(h.getLevel(), 1000);
        return Blueprint.capture(h.getLevel(), sel, true, h.getLevel().registryAccess(), new BlueprintMeta("Probe", "test", 1L, "renovator"));
    }

    static boolean sameCells(Blueprint a, Blueprint b) {
        if (!a.size().equals(b.size())) return false;
        for (int x = 0; x < a.size().getX(); x++)
            for (int y = 0; y < a.size().getY(); y++)
                for (int z = 0; z < a.size().getZ(); z++) {
                    BlockState sa = a.state(x, y, z), sb = b.state(x, y, z);
                    if (sa != sb) return false;
                }
        return true;
    }

    @GameTest(template = T)
    public static void captureWriteRead(GameTestHelper h) throws Exception {
        Blueprint bp = sample(h);
        h.assertTrue(bp.size().equals(new Vec3i(3, 2, 2)), "size " + bp.size());
        h.assertTrue(bp.state(0, 0, 0).is(Blocks.OAK_PLANKS) && bp.state(1, 1, 1).isAir(), "planks and explicit air");
        h.assertTrue(bp.nonAir() == 4, "4 blocks, was " + bp.nonAir());
        Path f = Files.createTempFile("renovator", ".nbt");
        BlueprintIO.write(bp, f);
        Blueprint back = BlueprintIO.read(f, BuiltInRegistries.BLOCK.asLookup());
        h.assertTrue(sameCells(bp, back), "file round trip");
        h.assertTrue(back.meta().name().equals("Probe"), "meta kept");
        Blueprint net = BlueprintIO.fromNetwork(BlueprintIO.toNetwork(bp), BuiltInRegistries.BLOCK.asLookup(), 1_000_000);
        h.assertTrue(sameCells(bp, net), "network round trip");
        h.assertTrue(net.blockEntity(2, 0, 1) != null && net.blockEntity(2, 0, 1).toString().contains("Bauplan"), "sign text travels");
        h.assertTrue(Arrays.equals(bp.sha1(), net.sha1()), "hash stable across round trip");
        h.succeed();
    }

    @GameTest(template = T)
    public static void rotateAndMirror(GameTestHelper h) {
        Blueprint bp = sample(h);
        Blueprint cw = Transform.apply(bp, Rotation.CLOCKWISE_90, Mirror.NONE);
        h.assertTrue(cw.size().equals(new Vec3i(2, 2, 3)), "rotated size " + cw.size());
        // stair at (1,0,0) → (sz-1-z, x) = (1, 1)
        BlockState stair = cw.state(1, 0, 1);
        h.assertTrue(stair != null && stair.is(Blocks.OAK_STAIRS) && stair.getValue(StairBlock.FACING) == Direction.EAST, "stair rotated: " + stair);
        Blueprint mirrored = Transform.apply(bp, Rotation.NONE, Mirror.FRONT_BACK);
        BlockState m = mirrored.state(1, 0, 0);
        h.assertTrue(m.is(Blocks.OAK_STAIRS) && m.getValue(StairBlock.FACING) == Direction.NORTH, "front-back mirror keeps north-facing x-mirror: " + m);
        Blueprint lr = Transform.apply(bp, Rotation.NONE, Mirror.LEFT_RIGHT);
        BlockState l = lr.state(1, 0, 1);
        h.assertTrue(l.is(Blocks.OAK_STAIRS) && l.getValue(StairBlock.FACING) == Direction.SOUTH, "left-right mirror flips north→south: " + l);
        Blueprint back = Transform.apply(Transform.apply(cw, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE), Rotation.NONE, Mirror.NONE);
        h.assertTrue(sameCells(bp, back), "cw then ccw is identity");
        h.succeed();
    }
}
