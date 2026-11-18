package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.family.Family;
import com.renovator.family.FamilyIndex;
import com.renovator.family.Shape;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class FamilyTests {
    private static final String T = RenovatorGameTests.T;

    static Family fam(String id) {
        return FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace(id));
    }

    static FamilyIndex.Member member(Block b) {
        return FamilyIndex.get().lookup(b);
    }

    @GameTest(template = T)
    public static void vanillaWoodFamilies(GameTestHelper h) {
        EnumSet<Shape> needed = EnumSet.of(Shape.FULL, Shape.STAIRS, Shape.SLAB, Shape.FENCE, Shape.FENCE_GATE, Shape.DOOR,
                Shape.TRAPDOOR, Shape.BUTTON, Shape.PRESSURE_PLATE, Shape.SIGN, Shape.WALL_SIGN, Shape.HANGING_SIGN,
                Shape.WALL_HANGING_SIGN, Shape.LOG, Shape.STRIPPED_LOG);
        for (String w : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")) {
            Family f = fam(w);
            h.assertTrue(f != null, "missing wood family " + w);
            h.assertTrue(f.wood(), w + " should be wood");
            for (Shape s : needed) h.assertTrue(f.has(s), w + " lacks " + s + " has " + f.blocks().keySet());
        }
        Family oak = fam("oak");
        h.assertTrue(oak.get(Shape.WOOD) == Blocks.OAK_WOOD && oak.get(Shape.STRIPPED_WOOD) == Blocks.STRIPPED_OAK_WOOD, "oak wood blocks");
        h.assertTrue(fam("crimson").get(Shape.LOG) == Blocks.CRIMSON_STEM, "crimson stem is its log");
        h.assertTrue(fam("bamboo").get(Shape.LOG) == Blocks.BAMBOO_BLOCK, "bamboo block is its log");
        h.assertTrue(member(Blocks.OAK_STAIRS).family() == oak, "oak stairs belong to oak");
        h.succeed();
    }

    @GameTest(template = T)
    public static void stoneFamilies(GameTestHelper h) {
        FamilyIndex.Member m = member(Blocks.STONE_BRICK_STAIRS);
        h.assertTrue(m != null && m.shape() == Shape.STAIRS, "stone brick stairs are stairs");
        Family f = m.family();
        h.assertTrue(f.id().getPath().equals("stone_bricks"), "family id stone_bricks, was " + f.id());
        h.assertTrue(f.get(Shape.FULL) == Blocks.STONE_BRICKS && f.get(Shape.SLAB) == Blocks.STONE_BRICK_SLAB
                && f.get(Shape.WALL) == Blocks.STONE_BRICK_WALL, "stone brick shapes " + f.blocks());
        h.assertTrue(member(Blocks.QUARTZ_STAIRS).family().get(Shape.FULL) == Blocks.QUARTZ_BLOCK, "quartz stairs → quartz block");
        h.assertTrue(member(Blocks.PURPUR_SLAB).family().get(Shape.FULL) == Blocks.PURPUR_BLOCK, "purpur slab → purpur block");
        h.assertTrue(member(Blocks.NETHER_BRICK_FENCE).family().get(Shape.FULL) == Blocks.NETHER_BRICKS, "nether brick fence");
        h.assertTrue(member(Blocks.STONE_BUTTON).family().get(Shape.FULL) == Blocks.STONE, "stone button joins stone");
        h.assertTrue(member(Blocks.IRON_DOOR).family() == member(Blocks.IRON_TRAPDOOR).family(), "iron door + trapdoor");
        h.assertTrue(member(Blocks.MOSSY_STONE_BRICKS).family() != member(Blocks.STONE_BRICKS).family(), "mossy is separate");
        h.assertTrue(member(Blocks.DEEPSLATE_TILE_STAIRS).family().get(Shape.FULL) == Blocks.DEEPSLATE_TILES, "deepslate tiles");
        h.succeed();
    }

    @GameTest(template = T)
    public static void panesAndSingletons(GameTestHelper h) {
        Family glass = member(Blocks.WHITE_STAINED_GLASS_PANE).family();
        h.assertTrue(glass.get(Shape.FULL) == Blocks.WHITE_STAINED_GLASS && glass.get(Shape.PANE) == Blocks.WHITE_STAINED_GLASS_PANE, "glass pane family");
        h.assertTrue(member(Blocks.WHITE_WOOL).family().singleton(), "wool is a singleton");
        h.assertTrue(member(Blocks.LANTERN).family().singleton(), "lantern is a singleton");
        h.assertTrue(member(Blocks.IRON_BARS).family().singleton(), "iron bars are a singleton");
        h.assertTrue(member(Blocks.FIRE) == null, "fire has no item → no family");
        h.assertTrue(member(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE).family().singleton(), "lone plate is a singleton");
        h.succeed();
    }

    @GameTest(template = T)
    public static void shapeChains(GameTestHelper h) {
        Family bricks = fam("stone_bricks");
        h.assertTrue(bricks.resolve(Shape.FENCE) == Shape.WALL, "fence → wall");
        h.assertTrue(bricks.resolve(Shape.DOOR) == null, "no door");
        h.assertTrue(bricks.resolve(Shape.LOG) == Shape.FULL, "log → full");
        h.assertTrue(fam("oak").resolve(Shape.WALL) == Shape.FENCE, "wall → fence");
        h.succeed();
    }
}
