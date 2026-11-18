package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.blueprint.Blueprint;
import com.renovator.family.Family;
import com.renovator.interior.Module;
import com.renovator.interior.ModuleLibrary;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every furniture block with a horizontal facing must turn with its module (otherwise it stands crosswise). */
@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class OrientationTests {
    private static final String T = RenovatorGameTests.T;
    private static final Logger LOG = LoggerFactory.getLogger("renovator/orientation");

    static Direction facing(BlockState s) {
        for (Property<?> p : s.getProperties()) {
            if (!p.getName().equals("facing")) continue;
            Object v = s.getValue(p);
            if (v instanceof Direction d && d.getAxis().isHorizontal()) return d;
        }
        return null;
    }

    @GameTest(template = T)
    public static void modulesRotateTheirBlocks(GameTestHelper h) {
        Family oak = InteriorTests.wood("oak");
        List<String> wrong = new ArrayList<>();
        for (String cat : ModuleLibrary.categories()) {
            for (Module m : ModuleLibrary.available(cat)) {
                Blueprint base = m.blueprint(DyeColor.WHITE, oak, Rotation.NONE);
                for (Rotation rot : new Rotation[]{Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90}) {
                    Blueprint turned = m.blueprint(DyeColor.WHITE, oak, rot);
                    var size = base.size();
                    for (int x = 0; x < size.getX(); x++)
                        for (int y = 0; y < size.getY(); y++)
                            for (int z = 0; z < size.getZ(); z++) {
                                BlockState s = base.state(x, y, z);
                                if (s == null || s.isAir()) continue;
                                Direction f = facing(s);
                                if (f == null) continue;
                                BlockPos t = turnedPos(x, z, size.getX(), size.getZ(), rot);
                                BlockState r = turned.state(t.getX(), y, t.getZ());
                                Direction got = r == null ? null : facing(r);
                                Direction want = rot.rotate(f);
                                // two-facing blocks (a symmetric desk: north/east) show the opposite of a missing direction
                                boolean symmetric = got == want.getOpposite() && !allows(r, want);
                                if (got != want && !symmetric) {
                                    wrong.add(m.id() + " " + BuiltInRegistries.BLOCK.getKey(s.getBlock()) + " " + f + " " + rot + " -> " + got
                                            + " (expected " + rot.rotate(f) + ")");
                                }
                            }
                }
            }
        }
        wrong.forEach(w -> LOG.warn("ORIENTATION {}", w));
        h.assertTrue(wrong.isEmpty(), wrong.size() + " furniture blocks do not turn with their module, first: " + (wrong.isEmpty() ? "" : wrong.get(0)));
        h.succeed();
    }

    static boolean allows(BlockState s, Direction d) {
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals("facing")) return p.getPossibleValues().contains(d);
        }
        return false;
    }

    /** Same position mapping as Transform (mirror NONE). */
    static BlockPos turnedPos(int x, int z, int sx, int sz, Rotation rot) {
        return switch (rot) {
            case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, 0, x);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, 0, sx - 1 - x);
            case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, 0, sz - 1 - z);
            default -> new BlockPos(x, 0, z);
        };
    }
}
