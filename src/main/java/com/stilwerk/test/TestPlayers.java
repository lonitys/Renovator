package com.stilwerk.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Fake players for tests; one fresh player per test (tests run in parallel). */
final class TestPlayers {
    private TestPlayers() {}

    static FakePlayer player(GameTestHelper h, GameType mode) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "stilwerk_test"));
        p.setGameMode(mode);
        BlockPos at = h.absolutePos(new BlockPos(8, StilwerkGameTests.FLOOR + 1, 8));
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return p;
    }

    static FakePlayer survival(GameTestHelper h) {
        return player(h, GameType.SURVIVAL);
    }

    static int count(FakePlayer p, Item item) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (ItemStack s : inv.items) if (s.is(item)) n += s.getCount();
        return n;
    }

    static void give(FakePlayer p, Item item, int n) {
        while (n > 0) {
            int c = Math.min(n, item.getDefaultMaxStackSize());
            p.getInventory().add(new ItemStack(item, c));
            n -= c;
        }
    }
}
