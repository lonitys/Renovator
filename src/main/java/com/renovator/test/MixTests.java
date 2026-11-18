package com.renovator.test;

import com.mojang.serialization.JsonOps;
import com.renovator.Renovator;
import com.renovator.mix.MixEntry;
import com.renovator.mix.MixPalette;
import com.renovator.mix.MixPattern;
import com.renovator.mix.Mixes;
import com.renovator.mix.PickContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class MixTests {
    private static final String T = RenovatorGameTests.T;
    private static final ResourceLocation A = ResourceLocation.withDefaultNamespace("stone_bricks");
    private static final ResourceLocation B = ResourceLocation.withDefaultNamespace("mossy_stone_bricks");
    private static final ResourceLocation C = ResourceLocation.withDefaultNamespace("cracked_stone_bricks");

    static MixPalette palette(MixPattern pattern) {
        return new MixPalette("test:p", "p", List.of(new MixEntry(A, 50), new MixEntry(B, 30), new MixEntry(C, 20)),
                pattern, Optional.of(ResourceLocation.withDefaultNamespace("polished_andesite")), Direction.Axis.Y, 0.12f);
    }

    static PickContext box(int sx, int sy, int sz) {
        LongOpenHashSet members = new LongOpenHashSet();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) members.add(BlockPos.asLong(x, y, z));
        return new PickContext(new BoundingBox(0, 0, 0, sx - 1, sy - 1, sz - 1), members);
    }

    @GameTest(template = T)
    public static void deterministicAndDistributed(GameTestHelper h) {
        MixPalette p1 = palette(MixPattern.SCATTER), p2 = palette(MixPattern.SCATTER);
        PickContext ctx = box(100, 100, 1);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int x = 0; x < 100; x++)
            for (int y = 0; y < 100; y++) {
                BlockPos p = new BlockPos(x, y, 0);
                ResourceLocation a = p1.pick(42L, p, ctx);
                h.assertTrue(a.equals(p2.pick(42L, p, ctx)), "same seed, same pick");
                counts.merge(a, 1, Integer::sum);
            }
        h.assertTrue(Math.abs(counts.get(A) - 5000) < 300 && Math.abs(counts.get(B) - 3000) < 300 && Math.abs(counts.get(C) - 2000) < 300,
                "weights respected: " + counts);
        h.succeed();
    }

    @GameTest(template = T)
    public static void gradientAndEdges(GameTestHelper h) {
        MixPalette g = palette(MixPattern.GRADIENT);
        PickContext col = box(1, 20, 1);
        int bottomA = 0, topC = 0;
        for (long seed = 0; seed < 20; seed++) {
            for (int y = 0; y < 4; y++) if (g.pick(seed, new BlockPos(0, y, 0), col).equals(A)) bottomA++;
            for (int y = 16; y < 20; y++) if (g.pick(seed, new BlockPos(0, y, 0), col).equals(C)) topC++;
        }
        h.assertTrue(bottomA >= 48, "first entry dominates the bottom: " + bottomA + "/80");
        h.assertTrue(topC >= 48, "last entry dominates the top: " + topC + "/80");
        PickContext wall = box(5, 5, 1);
        h.assertTrue(wall.isEdge(new BlockPos(0, 0, 0)), "corner is an edge");
        h.assertTrue(wall.isEdge(new BlockPos(2, 4, 0)), "top border is an edge");
        h.assertFalse(wall.isEdge(new BlockPos(2, 2, 0)), "centre is not an edge");
        MixPalette e = palette(MixPattern.EDGES);
        h.assertTrue(e.pick(1, new BlockPos(0, 0, 0), wall).getPath().equals("polished_andesite"), "edge block at the corner");
        h.succeed();
    }

    @GameTest(template = T)
    public static void codecAndBuiltIns(GameTestHelper h) {
        MixPalette p = palette(MixPattern.PATCHES);
        MixPalette back = MixPalette.CODEC.parse(JsonOps.INSTANCE, MixPalette.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()).getOrThrow();
        h.assertTrue(back.equals(p), "codec round trip");
        List<MixPalette> builtIn = Mixes.builtIn();
        h.assertTrue(builtIn.size() >= 7, "at least 7 built-in mixes, got " + builtIn.size());
        for (MixPalette b : builtIn) h.assertTrue(b.entries().size() >= 2, b.id() + " kept its entries");
        h.succeed();
    }
}
