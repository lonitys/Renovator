package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.compat.ProjectECompat;
import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.MaterialLedger;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class LedgerTests {
    private static final String T = StilwerkGameTests.T;

    static List<Change> swaps(int n, Block from, Block to) {
        List<Change> g = new ArrayList<>();
        for (int i = 0; i < n; i++) g.add(new Change(new BlockPos(i, 0, 0), from.defaultBlockState(), to.defaultBlockState(), null, false));
        return g;
    }

    @GameTest(template = T)
    public static void ledgerPaysFromInventory(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        TestPlayers.give(p, Items.SPRUCE_PLANKS, 10);
        MaterialLedger ledger = new MaterialLedger(p, p.blockPosition(), false);
        List<Change> group = swaps(4, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
        h.assertTrue(ledger.admit(group), "affordable group admitted");
        h.assertTrue(TestPlayers.count(p, Items.SPRUCE_PLANKS) == 6, "4 spruce taken");
        ledger.settled(group);
        h.assertTrue(TestPlayers.count(p, Items.OAK_PLANKS) == 4, "4 oak refunded");
        h.succeed();
    }

    @GameTest(template = T)
    public static void ledgerRejectsShortGroup(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        TestPlayers.give(p, Items.SPRUCE_PLANKS, 1);
        MaterialLedger ledger = new MaterialLedger(p, p.blockPosition(), false);
        h.assertFalse(ledger.admit(swaps(2, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS)), "short group rejected");
        h.assertTrue(TestPlayers.count(p, Items.SPRUCE_PLANKS) == 1, "nothing taken");
        h.succeed();
    }

    @GameTest(template = T)
    public static void ledgerReport(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        TestPlayers.give(p, Items.SPRUCE_STAIRS, 12);
        MaterialLedger ledger = new MaterialLedger(p, p.blockPosition(), false);
        ChangeSet set = ChangeSet.builder().group(swaps(30, Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS)).build();
        MaterialLedger.Report r = ledger.check(set);
        h.assertFalse(r.complete(), "incomplete");
        MaterialLedger.Line line = r.lines().get(0);
        h.assertTrue(line.item() == Items.SPRUCE_STAIRS && line.need() == 30 && line.inInventory() == 12 && line.missing() == 18,
                "line " + line);
        h.succeed();
    }

    @GameTest(template = T)
    public static void ledgerRefundOverflowDrops(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        MaterialLedger ledger = new MaterialLedger(p, p.blockPosition(), false);
        ledger.settled(swaps(3, Blocks.OAK_PLANKS, Blocks.AIR));
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(2),
                e -> e.getItem().is(Items.OAK_PLANKS));
        int dropped = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(dropped == 3, "3 planks dropped, was " + dropped);
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    /** In-memory EMC account: spruce planks learned at 32 EMC, oak at 32 but unlearned. */
    static final class FakeBank implements com.stilwerk.core.EmcBank {
        BigInteger emc;
        int syncs;

        FakeBank(long emc) {
            this.emc = BigInteger.valueOf(emc);
        }

        @Override public BigInteger emc() { return emc; }
        @Override public void setEmc(BigInteger value) { emc = value; }
        @Override public boolean knows(net.minecraft.world.item.Item item) { return item == Items.SPRUCE_PLANKS; }
        @Override public long value(net.minecraft.world.item.Item item) { return item == Items.SPRUCE_PLANKS || item == Items.OAK_PLANKS ? 32 : 0; }
        @Override public void sync() { syncs++; }
    }

    @GameTest(template = T)
    public static void ledgerEmc(GameTestHelper h) {
        FakePlayer p = TestPlayers.survival(h);
        TestPlayers.give(p, Items.SPRUCE_PLANKS, 1);
        FakeBank bank = new FakeBank(32 * 10);
        MaterialLedger ledger = new MaterialLedger(p, p.blockPosition(), bank);
        MaterialLedger.Report r = ledger.check(ChangeSet.builder().group(swaps(4, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS)).build());
        h.assertTrue(r.complete() && r.lines().get(0).viaEmc(), "report covers the rest with EMC: " + r);
        h.assertTrue(ledger.admit(swaps(4, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS)), "1 from inventory + 3 from EMC");
        h.assertTrue(TestPlayers.count(p, Items.SPRUCE_PLANKS) == 0 && bank.emc.longValue() == 32 * 7, "charged 3x32, emc=" + bank.emc);
        h.assertFalse(ledger.admit(swaps(2, Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS)), "unlearned oak cannot be made from EMC");
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        ledger.settled(swaps(2, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS));
        h.assertTrue(bank.emc.longValue() == 32 * 9, "overflow refund became EMC, emc=" + bank.emc);
        ledger.finished();
        h.assertTrue(bank.syncs == 1, "synced once");
        h.succeed();
    }

    @GameTest(template = T)
    public static void projectEReflection(GameTestHelper h) {
        // Only meaningful with -PwithProjectE; values are not mapped on the GameTest server, so only the API wiring is checked.
        if (ProjectECompat.available()) {
            FakePlayer p = TestPlayers.survival(h);
            h.assertTrue(ProjectECompat.bank(p) != null, "bank available");
            ProjectECompat.setEmc(p, BigInteger.valueOf(1234));
            h.assertTrue(ProjectECompat.emc(p).longValue() == 1234, "emc round trip through reflection");
            ProjectECompat.knows(p, Items.OAK_PLANKS);
        }
        h.succeed();
    }
}
