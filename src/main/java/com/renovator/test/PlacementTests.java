package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.blueprint.Blueprint;
import com.renovator.blueprint.BlueprintMeta;
import com.renovator.blueprint.BlueprintPlanner;
import com.renovator.blueprint.Transform;
import com.renovator.core.Applier;
import com.renovator.core.Change;
import com.renovator.core.CreativeGate;
import com.renovator.core.Selection;
import com.renovator.swap.SwapMapping;
import com.renovator.swap.Target;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class PlacementTests {
    private static final String T = RenovatorGameTests.T;
    private static final int Y = RenovatorGameTests.FLOOR + 1;

    static BlueprintPlanner.Result plan(GameTestHelper h, Blueprint bp, BlockPos rel, boolean overwrite, boolean survival, SwapMapping m) {
        return BlueprintPlanner.plan(h.getLevel(), bp, h.absolutePos(rel), m, Map.of(), 1, new BlueprintPlanner.Options(overwrite, survival));
    }

    @GameTest(template = T)
    public static void placeCopyElsewhere(GameTestHelper h) {
        Blueprint bp = BlueprintTests.sample(h); // occupies x 2..4, z 2..3
        Applier.applyNow(h.getLevel(), plan(h, bp, new BlockPos(8, Y, 8), false, false, SwapMapping.EMPTY).changes(), CreativeGate.INSTANCE);
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(8, Y, 8));
        BlockState stair = h.getBlockState(new BlockPos(9, Y, 8));
        h.assertTrue(stair.is(Blocks.OAK_STAIRS) && stair.getValue(StairBlock.FACING) == Direction.NORTH, "stair copied " + stair);
        h.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(8, Y + 1, 8));
        h.assertBlockPresent(Blocks.OAK_SIGN, new BlockPos(10, Y, 9));
        h.succeed();
    }

    @GameTest(template = T)
    public static void placeRotatedAndRestyled(GameTestHelper h) {
        Blueprint bp = Transform.apply(BlueprintTests.sample(h), Rotation.CLOCKWISE_90, Mirror.NONE);
        SwapMapping spruce = SwapMapping.EMPTY.with(ResourceLocation.withDefaultNamespace("oak"), Target.family(ResourceLocation.withDefaultNamespace("spruce")));
        BlueprintPlanner.Result r = plan(h, bp, new BlockPos(8, Y, 8), false, false, spruce);
        Applier.applyNow(h.getLevel(), r.changes(), CreativeGate.INSTANCE);
        BlockState stair = h.getBlockState(new BlockPos(9, Y, 9));
        h.assertTrue(stair.is(Blocks.SPRUCE_STAIRS) && stair.getValue(StairBlock.FACING) == Direction.EAST, "rotated + restyled stair " + stair);
        h.assertBlockPresent(Blocks.SPRUCE_SIGN, new BlockPos(8, Y, 10));
        h.succeed();
    }

    @GameTest(template = T)
    public static void survivalOnlyIntoAirOrReplaceable(GameTestHelper h) {
        Blueprint bp = new Blueprint.Builder(new Vec3i(3, 1, 1), BlueprintMeta.UNNAMED)
                .set(0, 0, 0, Blocks.OAK_PLANKS.defaultBlockState(), null)
                .set(1, 0, 0, Blocks.OAK_PLANKS.defaultBlockState(), null)
                .set(2, 0, 0, Blocks.OAK_PLANKS.defaultBlockState(), null).build();
        h.setBlock(new BlockPos(9, Y, 5), Blocks.STONE);
        h.setBlock(new BlockPos(10, Y, 5), Blocks.SHORT_GRASS);
        BlueprintPlanner.Result r = plan(h, bp, new BlockPos(8, Y, 5), false, true, SwapMapping.EMPTY);
        h.assertTrue(r.blocked() == 1, "stone blocks one cell, blocked=" + r.blocked());
        h.assertTrue(r.changes().size() == 2, "two cells placed (air + grass), was " + r.changes().size());
        h.succeed();
    }

    @GameTest(template = T)
    public static void survivalStripsBlockEntities(GameTestHelper h) {
        h.setBlock(new BlockPos(2, Y, 2), Blocks.CHEST);
        if (h.getBlockEntity(new BlockPos(2, Y, 2)) instanceof ChestBlockEntity chest) chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        Blueprint bp = Blueprint.capture(h.getLevel(),
                new Selection.Box(h.absolutePos(new BlockPos(2, Y, 2)), h.absolutePos(new BlockPos(2, Y, 2))).resolve(h.getLevel(), 10),
                false, h.getLevel().registryAccess(), BlueprintMeta.UNNAMED);
        h.assertTrue(bp.blockEntity(0, 0, 0) != null, "capture keeps chest data");
        Change survival = plan(h, bp, new BlockPos(8, Y, 8), false, true, SwapMapping.EMPTY).changes().changes().get(0);
        h.assertTrue(survival.blockEntity() == null, "survival places an empty chest");
        Change creative = plan(h, bp, new BlockPos(8, Y, 8), false, false, SwapMapping.EMPTY).changes().changes().get(0);
        h.assertTrue(creative.blockEntity() != null, "creative keeps the contents");
        h.succeed();
    }

    @GameTest(template = T)
    public static void overwriteClearsWithAir(GameTestHelper h) {
        Blueprint bp = new Blueprint.Builder(new Vec3i(2, 1, 1), BlueprintMeta.UNNAMED)
                .set(0, 0, 0, Blocks.OAK_PLANKS.defaultBlockState(), null).air(1, 0, 0).build();
        h.setBlock(new BlockPos(9, Y, 3), Blocks.DIRT);
        h.assertTrue(plan(h, bp, new BlockPos(8, Y, 3), false, false, SwapMapping.EMPTY).changes().size() == 1, "without overwrite dirt stays");
        BlueprintPlanner.Result r = plan(h, bp, new BlockPos(8, Y, 3), true, false, SwapMapping.EMPTY);
        Applier.applyNow(h.getLevel(), r.changes(), CreativeGate.INSTANCE);
        h.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 3));
        h.assertTrue(plan(h, bp, new BlockPos(8, Y, 3), true, true, SwapMapping.EMPTY).changes().isEmpty(), "survival never clears");
        h.succeed();
    }
}
