package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.blueprint.Blueprint;
import com.renovator.blueprint.BlueprintIO;
import com.renovator.core.Selection;
import com.renovator.core.UndoHistory;
import com.renovator.net.PlaceRequest;
import com.renovator.config.ServerConfig;
import com.renovator.net.ServerOps;
import com.renovator.net.SmallPayloads;
import com.renovator.net.SwapRequest;
import com.renovator.net.UploadBuffer;
import com.renovator.swap.SwapMapping;
import com.renovator.swap.Target;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class ServerOpsTests {
    private static final String T = RenovatorGameTests.T;
    private static final int Y = SwapTests.Y;
    static final SwapMapping OAK_TO_SPRUCE = SwapMapping.EMPTY.with(ResourceLocation.withDefaultNamespace("oak"),
            Target.family(ResourceLocation.withDefaultNamespace("spruce")));

    static Selection box(GameTestHelper h) {
        return new Selection.Box(h.absolutePos(new BlockPos(0, Y, 0)), h.absolutePos(new BlockPos(14, Y + 4, 14)));
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void creativeSwapAndUndo(GameTestHelper h) {
        SwapTests.oakParts(h);
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        ServerOps.swap(p, new SwapRequest(1, box(h), OAK_TO_SPRUCE, List.of(), 3, false, com.renovator.net.BuildMode.ASK));
        h.startSequence()
                .thenWaitUntil(() -> h.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(1, Y, 1)))
                .thenWaitUntil(() -> h.assertTrue(UndoHistory.depth(h.getLevel().getServer(), p.getUUID()) == 1, "undo recorded"))
                .thenExecute(() -> ServerOps.undo(p))
                .thenWaitUntil(() -> h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, Y, 1)))
                .thenWaitUntil(() -> h.assertBlockPresent(Blocks.OAK_DOOR, new BlockPos(4, Y + 1, 3)))
                .thenSucceed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void survivalNeedsMaterials(GameTestHelper h) {
        SwapTests.oakParts(h);
        FakePlayer p = TestPlayers.survival(h);
        ServerOps.swap(p, new SwapRequest(5, box(h), OAK_TO_SPRUCE, List.of(), 3, false, com.renovator.net.BuildMode.ASK));
        h.assertTrue(ServerOps.pendingForTests().containsKey(p.getUUID()), "pending report without materials");
        TestPlayers.give(p, Items.SPRUCE_PLANKS, 4);
        ServerOps.confirm(p, new SmallPayloads.ConfirmRequest(5, SmallPayloads.ConfirmAction.BUILD));
        h.startSequence()
                .thenWaitUntil(() -> h.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(4, Y, 1)))
                .thenExecute(() -> {
                    h.assertBlockPresent(Blocks.OAK_STAIRS, new BlockPos(1, Y, 2));
                    h.assertTrue(TestPlayers.count(p, Items.SPRUCE_PLANKS) == 0, "planks spent");
                    h.assertTrue(TestPlayers.count(p, Items.OAK_PLANKS) == 4, "oak planks refunded");
                })
                .thenSucceed();
    }

    @GameTest(template = T)
    public static void rejectsTooBigWhenLimited(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        Selection huge = new Selection.Box(h.absolutePos(new BlockPos(0, Y, 0)), h.absolutePos(new BlockPos(40, Y + 40, 40)));
        ServerConfig.maxBlocksOverride = 20_000;
        try {
            ServerOps.swap(p, new SwapRequest(9, huge, OAK_TO_SPRUCE, List.of(), 1, false, com.renovator.net.BuildMode.ASK));
        } finally {
            ServerConfig.maxBlocksOverride = -1;
        }
        h.assertTrue("renovator.error.too_big".equals(ServerOps.lastMessageForTests(p.getUUID())), "too big: " + ServerOps.lastMessageForTests(p.getUUID()));
        h.assertFalse(ServerOps.pendingForTests().containsKey(p.getUUID()), "nothing pending");
        h.succeed();
    }

    @GameTest(template = T)
    public static void noBlockLimitByDefault(GameTestHelper h) {
        h.assertTrue(ServerConfig.maxBlocks(false) == Integer.MAX_VALUE && ServerConfig.maxBlocks(true) == Integer.MAX_VALUE,
                "no block limit: survival " + ServerConfig.maxBlocks(false) + ", creative " + ServerConfig.maxBlocks(true));
        FakePlayer p = TestPlayers.survival(h);
        Selection big = new Selection.Box(h.absolutePos(new BlockPos(0, Y, 0)), h.absolutePos(new BlockPos(40, Y + 40, 40)));
        ServerOps.swap(p, new SwapRequest(10, big, OAK_TO_SPRUCE, List.of(), 1, false, com.renovator.net.BuildMode.ASK));
        h.assertFalse("renovator.error.too_big".equals(ServerOps.lastMessageForTests(p.getUUID())), "68 921 blocks are not too big");
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void uploadAndPlace(GameTestHelper h) {
        Blueprint bp = BlueprintTests.sample(h);
        FakePlayer p = TestPlayers.player(h, GameType.CREATIVE);
        byte[] data = BlueprintIO.toNetwork(bp);
        int chunk = 64; // force several chunks
        int total = (data.length + chunk - 1) / chunk;
        for (int i = 0; i < total; i++) {
            UploadBuffer.accept(p, new SmallPayloads.BlueprintChunk(7, i, total, Arrays.copyOfRange(data, i * chunk, Math.min(data.length, (i + 1) * chunk))));
        }
        h.assertTrue(UploadBuffer.get(p, bp.sha1()) != null, "assembled from " + total + " chunks");
        ServerOps.place(p, new PlaceRequest(2, bp.sha1(), h.absolutePos(new BlockPos(9, Y, 9)), Rotation.NONE, Mirror.NONE,
                SwapMapping.EMPTY, List.of(), 1, false, com.renovator.net.BuildMode.ASK));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(9, Y, 9));
            h.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(9, Y + 1, 9));
        });
    }
}
