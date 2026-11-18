package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.core.Selection;
import com.stilwerk.detail.DetailPlanner;
import com.stilwerk.detail.DetailTypes.Choice;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.net.DetailRequest;
import com.stilwerk.net.ServerOps;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class DetailOpsTests {
    private static final String T = StilwerkGameTests.T;
    private static final int Y0 = DetailTests.Y0;

    static Selection sel(GameTestHelper h) {
        return new Selection.Box(h.absolutePos(new BlockPos(0, Y0, 0)), h.absolutePos(new BlockPos(10, Y0 + 7, 9)));
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void creativeDetailRequest(GameTestHelper h) {
        DetailTests.house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        Suggestion window = DetailPlanner.find(h.getLevel(), DetailTests.box(h), 9).stream()
                .filter(s -> s.rule().equals("window")).findFirst().orElseThrow();
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        ServerOps.detail(p, new DetailRequest(1, Optional.of(sel(h)), Optional.empty(), 9,
                List.of(new Choice("window", window.anchor(), 1)), false));
        h.succeedWhen(() -> h.assertBlockPresent(Blocks.OAK_STAIRS, new BlockPos(3, Y0 + 3, 8)));
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void targetedRequest(GameTestHelper h) {
        DetailTests.house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        BlockPos clicked = h.absolutePos(new BlockPos(5, Y0 + 1, 7));
        Suggestion entrance = DetailPlanner.near(h.getLevel(), clicked, 4).stream()
                .filter(s -> s.rule().equals("entrance")).findFirst().orElseThrow();
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        ServerOps.detail(p, new DetailRequest(2, Optional.empty(), Optional.of(clicked), 4,
                List.of(new Choice("entrance", entrance.anchor(), 1)), false));
        h.succeedWhen(() -> h.assertBlockPresent(Blocks.OAK_SLAB, new BlockPos(5, Y0 + 3, 8)));
    }

    @GameTest(template = T)
    public static void survivalDetailNeedsMaterials(GameTestHelper h) {
        DetailTests.house(h, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR);
        Suggestion window = DetailPlanner.find(h.getLevel(), DetailTests.box(h), 9).stream()
                .filter(s -> s.rule().equals("window")).findFirst().orElseThrow();
        FakePlayer p = TestPlayers.survival(h);
        ServerOps.detail(p, new DetailRequest(3, Optional.of(sel(h)), Optional.empty(), 9,
                List.of(new Choice("window", window.anchor(), 0)), false));
        h.assertTrue(ServerOps.pendingForTests().containsKey(p.getUUID()), "material report pending");
        h.succeed();
    }
}
