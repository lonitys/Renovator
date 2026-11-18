package com.stilwerk.core;

import com.stilwerk.compat.ProjectECompat;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Survival payment: blocks come from the inventory first, then (with ProjectE) from EMC for learned items.
 * Replaced blocks go back into the inventory, overflow becomes EMC or is dropped.
 */
public final class MaterialLedger implements Gate {
    public record Line(Item item, int need, int inInventory, boolean viaEmc, int missing) {}

    public record Report(List<Line> lines, boolean complete) {}

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos dropFallback;
    private final @org.jetbrains.annotations.Nullable EmcBank bank;
    private final boolean useEmc;
    private boolean emcDirty;

    /** With {@code useEmc} the player's ProjectE account pays what the inventory cannot (if ProjectE is installed). */
    public MaterialLedger(ServerPlayer player, BlockPos dropFallback, boolean useEmc) {
        this(player, dropFallback, useEmc ? ProjectECompat.bank(player) : null);
    }

    public MaterialLedger(ServerPlayer player, BlockPos dropFallback, @org.jetbrains.annotations.Nullable EmcBank bank) {
        this.player = player;
        this.level = player.serverLevel();
        this.dropFallback = dropFallback.immutable();
        this.bank = bank;
        this.useEmc = bank != null;
    }

    // ---------------------------------------------------------------- report

    public Report check(ChangeSet set) {
        List<net.minecraft.world.level.block.state.BlockState> states = new ArrayList<>(set.size());
        for (Change c : set.changes()) states.add(c.newState());
        Object2IntOpenHashMap<Item> need = Costs.tally(states);
        BigInteger emcLeft = useEmc ? bank.emc() : BigInteger.ZERO;
        List<Item> items = new ArrayList<>(need.keySet());
        items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        List<Line> lines = new ArrayList<>();
        boolean complete = true;
        for (Item item : items) {
            int n = need.getInt(item);
            int have = count(item);
            int remaining = Math.max(0, n - have);
            boolean viaEmc = false;
            if (remaining > 0 && useEmc && bank.knows(item)) {
                long value = bank.value(item);
                if (value > 0) {
                    BigInteger v = BigInteger.valueOf(value);
                    long affordable = emcLeft.divide(v).min(BigInteger.valueOf(remaining)).longValue();
                    if (affordable > 0) {
                        viaEmc = true;
                        emcLeft = emcLeft.subtract(v.multiply(BigInteger.valueOf(affordable)));
                        remaining -= (int) affordable;
                    }
                }
            }
            if (remaining > 0) complete = false;
            lines.add(new Line(item, n, Math.min(have, n), viaEmc, remaining));
        }
        return new Report(lines, complete);
    }

    // ---------------------------------------------------------------- gate

    @Override
    public boolean admit(List<Change> group) {
        List<net.minecraft.world.level.block.state.BlockState> states = new ArrayList<>(group.size());
        for (Change c : group) states.add(c.newState());
        Object2IntOpenHashMap<Item> need = Costs.tally(states);
        // plan first, take only when everything is affordable
        BigInteger emcNeeded = BigInteger.ZERO;
        Object2IntOpenHashMap<Item> fromInventory = new Object2IntOpenHashMap<>();
        for (Object2IntMap.Entry<Item> e : need.object2IntEntrySet()) {
            Item item = e.getKey();
            int n = e.getIntValue();
            int have = count(item);
            int fromInv = Math.min(have, n);
            fromInventory.put(item, fromInv);
            int rest = n - fromInv;
            if (rest > 0) {
                if (!useEmc || !bank.knows(item)) return false;
                long value = bank.value(item);
                if (value <= 0) return false;
                emcNeeded = emcNeeded.add(BigInteger.valueOf(value).multiply(BigInteger.valueOf(rest)));
            }
        }
        if (emcNeeded.signum() > 0) {
            BigInteger emc = bank.emc();
            if (emc.compareTo(emcNeeded) < 0) return false;
            bank.setEmc(emc.subtract(emcNeeded));
            emcDirty = true;
        }
        for (Object2IntMap.Entry<Item> e : fromInventory.object2IntEntrySet()) take(e.getKey(), e.getIntValue());
        return true;
    }

    @Override
    public void settled(List<Change> applied) {
        List<net.minecraft.world.level.block.state.BlockState> states = new ArrayList<>(applied.size());
        for (Change c : applied) states.add(c.oldState());
        for (Object2IntMap.Entry<Item> e : Costs.tally(states).object2IntEntrySet()) give(e.getKey(), e.getIntValue());
    }

    @Override
    public void finished() {
        if (emcDirty && bank != null) bank.sync();
        emcDirty = false;
        player.containerMenu.broadcastChanges();
    }

    // ---------------------------------------------------------------- inventory

    private Inventory inv() {
        return player.getInventory();
    }

    /** Count in main inventory + offhand (armor never pays). */
    int count(Item item) {
        int n = 0;
        for (ItemStack s : inv().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : inv().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    private void take(Item item, int n) {
        for (ItemStack s : inv().items) {
            if (n <= 0) return;
            if (s.is(item)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
        for (ItemStack s : inv().offhand) {
            if (n <= 0) return;
            if (s.is(item)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }

    private void give(Item item, int n) {
        int max = new ItemStack(item).getMaxStackSize();
        while (n > 0) {
            int c = Math.min(n, max);
            ItemStack stack = new ItemStack(item, c);
            boolean online = !player.isRemoved() && player.isAlive();
            if (online) inv().add(stack);
            int left = stack.getCount();
            if (left > 0 && useEmc) {
                long value = bank.value(item);
                if (value > 0) {
                    bank.setEmc(bank.emc().add(BigInteger.valueOf(value).multiply(BigInteger.valueOf(left))));
                    emcDirty = true;
                    left = 0;
                }
            }
            if (left > 0) drop(new ItemStack(item, left), online);
            n -= c;
        }
    }

    private void drop(ItemStack stack, boolean online) {
        double x, y, z;
        if (online && player.level() == level) {
            x = player.getX();
            y = player.getY() + 0.5;
            z = player.getZ();
        } else {
            x = dropFallback.getX() + 0.5;
            y = dropFallback.getY() + 0.5;
            z = dropFallback.getZ() + 0.5;
        }
        ItemEntity e = new ItemEntity(level, x, y, z, stack);
        e.setNoPickUpDelay();
        level.addFreshEntity(e);
    }
}
