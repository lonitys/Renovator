package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.config.ServerConfig;
import com.stilwerk.core.Applier;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.CreativeGate;
import com.stilwerk.core.UndoHistory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class UndoTests {
    private static final String T = StilwerkGameTests.T;
    private static final int Y = StilwerkGameTests.FLOOR + 1;

    private static UndoHistory.Entry entry(GameTestHelper h, int x) {
        h.setBlock(new BlockPos(x, Y, 1), Blocks.OAK_PLANKS);
        return new UndoHistory.Entry(Level.OVERWORLD, ChangeSet.builder()
                .add(ApplierTests.swap(h, new BlockPos(x, Y, 1), Blocks.SPRUCE_PLANKS, false)).build(), x);
    }

    @GameTest(template = T)
    public static void undoStackAndPersistence(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID id = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) UndoHistory.push(server, id, entry(h, i));
        h.assertTrue(UndoHistory.depth(server, id) == 3, "depth 3");
        UndoHistory.clearCacheForTests(server);
        h.assertTrue(UndoHistory.depth(server, id) == 3, "depth survives reload from file");
        UndoHistory.Entry top = UndoHistory.pop(server, id).orElseThrow();
        h.assertTrue(top.time() == 3, "newest popped first");
        while (UndoHistory.pop(server, id).isPresent()) { /* drain */ }
        h.succeed();
    }

    @GameTest(template = T)
    public static void undoTrimsToSteps(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID id = UUID.randomUUID();
        ServerConfig.undoStepsOverride = 2;
        try {
            for (int i = 1; i <= 3; i++) UndoHistory.push(server, id, entry(h, i));
            h.assertTrue(UndoHistory.depth(server, id) == 2, "trimmed to 2");
            h.assertTrue(UndoHistory.pop(server, id).orElseThrow().time() == 3, "newest kept");
            h.assertTrue(UndoHistory.pop(server, id).orElseThrow().time() == 2, "oldest dropped");
        } finally {
            ServerConfig.undoStepsOverride = -1;
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void undoRestoresAndSkipsChanged(GameTestHelper h) {
        for (int x = 1; x <= 2; x++) h.setBlock(new BlockPos(x, Y, 5), Blocks.OAK_PLANKS);
        ChangeSet set = ChangeSet.builder()
                .add(ApplierTests.swap(h, new BlockPos(1, Y, 5), Blocks.SPRUCE_PLANKS, false))
                .add(ApplierTests.swap(h, new BlockPos(2, Y, 5), Blocks.SPRUCE_PLANKS, false)).build();
        ChangeSet applied = Applier.applyNow(h.getLevel(), set, CreativeGate.INSTANCE);
        h.setBlock(new BlockPos(2, Y, 5), Blocks.GOLD_BLOCK); // someone changed it afterwards
        Applier.applyNow(h.getLevel(), applied.inverse(), CreativeGate.INSTANCE);
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, Y, 5));
        h.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, Y, 5));
        h.succeed();
    }
}
