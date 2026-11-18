package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.importer.ImportResult;
import com.stilwerk.blueprint.importer.Importers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class ImportTests {
    private static final String T = StilwerkGameTests.T;

    static InputStream fixture(String name) {
        InputStream in = ImportTests.class.getResourceAsStream("/data/stilwerk/stilwerk_fixtures/" + name);
        if (in == null) throw new IllegalStateException("missing fixture " + name);
        return in;
    }

    static ImportResult load(String name) {
        try (InputStream in = fixture(name)) {
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            String ext = name.substring(name.lastIndexOf('.') + 1);
            return Importers.load(root, ext, name, BuiltInRegistries.BLOCK.asLookup());
        } catch (IOException e) {
            throw new RuntimeException(name + ": " + e.getMessage(), e);
        }
    }

    static void checkHouse(GameTestHelper h, Blueprint bp, String label) {
        h.assertTrue(bp.size().equals(new Vec3i(4, 3, 5)), label + " size " + bp.size());
        h.assertTrue(bp.state(2, 0, 3).is(Blocks.STONE_BRICKS), label + " floor");
        BlockState stairs = bp.state(1, 1, 0);
        h.assertTrue(stairs.is(Blocks.OAK_STAIRS) && stairs.getValue(StairBlock.FACING) == Direction.EAST
                && stairs.getValue(StairBlock.HALF) == Half.TOP, label + " stairs " + stairs);
        h.assertTrue(bp.state(2, 1, 2).is(Blocks.OAK_PLANKS), label + " planks");
        h.assertTrue(bp.state(0, 2, 0).isAir(), label + " air is explicit");
        BlockState high = bp.state(3, 2, 4);
        h.assertTrue(high.getBlock() instanceof StairBlock, label + " palette id >= 128 decoded: " + high);
        CompoundTag sign = bp.blockEntity(0, 1, 0);
        h.assertTrue(bp.state(0, 1, 0).is(Blocks.OAK_SIGN) && sign != null && sign.toString().contains("Hallo"), label + " sign " + sign);
    }

    @GameTest(template = T)
    public static void spongeV2AndV3(GameTestHelper h) {
        ImportResult v2 = load("house_v2.schem"), v3 = load("house_v3.schem");
        checkHouse(h, v2.blueprint(), "v2");
        checkHouse(h, v3.blueprint(), "v3");
        h.assertTrue(v2.blueprint().state(3, 2, 4) == v3.blueprint().state(3, 2, 4), "v2 and v3 agree");
        h.assertTrue(v2.warnings().isEmpty(), "no warnings: " + v2.warnings());
        h.succeed();
    }

    @GameTest(template = T)
    public static void upgradesAndUnknownBlocks(GameTestHelper h) {
        BlockState grass = load("old_grass.schem").blueprint().state(0, 0, 0);
        h.assertTrue(grass.is(Blocks.SHORT_GRASS), "1.20.1 grass upgraded to short_grass: " + grass);
        ImportResult unknown = load("unknown.schem");
        h.assertTrue(unknown.blueprint().state(0, 0, 0).isAir() && unknown.blueprint().state(1, 0, 0).is(Blocks.STONE), "unknown → air");
        h.assertTrue(unknown.warnings().size() == 1 && unknown.warnings().get(0).contains("nosuchmod:block"), "warning " + unknown.warnings());
        h.succeed();
    }

    @GameTest(template = T)
    public static void litematicRegions(GameTestHelper h) throws IOException {
        Blueprint bp = load("multi.litematic").blueprint();
        h.assertTrue(bp.size().equals(new Vec3i(6, 3, 3)), "merged size " + bp.size());
        h.assertTrue(bp.meta().name().equals("Zwei Regionen"), "name from metadata");
        String[] expected;
        try (InputStream in = fixture("multi_expected.txt")) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().split(" ");
        }
        BlockState[] pal = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState()};
        int i = 0;
        for (int y = 0; y < 3; y++)
            for (int z = 0; z < 3; z++)
                for (int x = 0; x < 4; x++, i++) {
                    int v = Integer.parseInt(expected[i]);
                    BlockState s = bp.state(x, y, z);
                    boolean ok = v < 4 ? s == pal[v] : s.is(Blocks.OAK_STAIRS) && s.getValue(StairBlock.FACING) == Direction.SOUTH;
                    h.assertTrue(ok, "region A cell " + x + "," + y + "," + z + " expected " + v + " got " + s);
                }
        h.assertTrue(bp.state(4, 0, 0).is(Blocks.GOLD_BLOCK) && bp.state(5, 0, 0).is(Blocks.GOLD_BLOCK), "region B with negative size");
        h.assertTrue(bp.state(4, 1, 0) == null, "outside every region is VOID");
        h.succeed();
    }

    @GameTest(template = T)
    public static void vanillaStructure(GameTestHelper h) {
        Blueprint bp = load("plain.nbt").blueprint();
        h.assertTrue(bp.state(0, 0, 0).is(Blocks.STONE), "stone");
        h.assertTrue(bp.state(1, 0, 0) != null && bp.state(1, 0, 0).isAir(), "explicit air");
        h.assertTrue(bp.state(2, 0, 0) == null, "omitted position is VOID");
        h.succeed();
    }
}
