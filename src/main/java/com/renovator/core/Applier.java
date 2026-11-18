package com.renovator.core;

import com.renovator.config.ServerConfig;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Applies change sets on the server, a budget of blocks per tick, groups never split.
 * States are set exactly (no neighbour shape updates); only reshaped blocks get their connections recomputed.
 */
public final class Applier {
    private Applier() {}

    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public record Job(UUID owner, ServerLevel level, ChangeSet set, Gate gate, Consumer<ChangeSet> onDone, @Nullable ServerPlayer progressTo) {}

    private static final class Running {
        final Job job;
        int group;
        int done;
        int ticks;
        final ChangeSet.Builder applied = ChangeSet.builder();
        final List<Change> reshape = new ArrayList<>();

        Running(Job job) {
            this.job = job;
        }

        boolean finished() {
            return group >= job.set().groups().size();
        }
    }

    private static final ArrayDeque<Running> QUEUE = new ArrayDeque<>();

    public static synchronized void submit(Job job) {
        QUEUE.add(new Running(job));
    }

    public static synchronized boolean busy(UUID owner) {
        for (Running r : QUEUE) if (r.job.owner().equals(owner)) return true;
        return false;
    }

    public static synchronized void clear() {
        QUEUE.clear();
    }

    /** Runs queued jobs in order within this tick's budget. */
    public static synchronized void tick(MinecraftServer server) {
        int budget = ServerConfig.blocksPerTick();
        Iterator<Running> it = QUEUE.iterator();
        while (budget > 0 && it.hasNext()) {
            Running r = it.next();
            r.ticks++;
            budget -= step(r, budget);
            if (r.finished()) {
                finish(r);
                it.remove();
            } else if (r.ticks % 10 == 1) {
                progress(r);
            }
        }
    }

    /** Applies groups up to the budget (at least one group); returns the number of changes visited. */
    private static int step(Running r, int budget) {
        List<List<Change>> groups = r.job.set().groups();
        int used = 0;
        while (r.group < groups.size() && (used == 0 || used + groups.get(r.group).size() <= budget)) {
            List<Change> g = groups.get(r.group++);
            used += g.size();
            r.done += g.size();
            List<Change> applied = applyGroup(r.job.level(), g, r.job.gate());
            if (!applied.isEmpty()) {
                r.applied.group(applied);
                for (Change c : applied) if (c.reshape()) r.reshape.add(c);
            }
        }
        return used;
    }

    private static void finish(Running r) {
        reshape(r.job.level(), r.reshape);
        r.job.gate().finished();
        ServerPlayer p = r.job.progressTo();
        if (p != null && p.connection != null && r.job.set().size() > ServerConfig.blocksPerTick()) {
            p.displayClientMessage(Component.translatable("renovator.progress", bar(1.0), 100), true);
        }
        r.job.onDone().accept(r.applied.build());
    }

    private static void progress(Running r) {
        ServerPlayer p = r.job.progressTo();
        if (p == null || p.connection == null) return;
        double f = r.done / (double) Math.max(1, r.job.set().size());
        p.displayClientMessage(Component.translatable("renovator.progress", bar(f), (int) Math.round(f * 100)), true);
    }

    private static String bar(double f) {
        int n = (int) Math.round(f * 20);
        return "▮".repeat(n) + "▯".repeat(20 - n);
    }

    /** Applies everything immediately (tests, tiny jobs). Returns what was applied. */
    public static ChangeSet applyNow(ServerLevel level, ChangeSet set, Gate gate) {
        ChangeSet.Builder applied = ChangeSet.builder();
        List<Change> reshape = new ArrayList<>();
        for (List<Change> g : set.groups()) {
            List<Change> a = applyGroup(level, g, gate);
            if (!a.isEmpty()) {
                applied.group(a);
                for (Change c : a) if (c.reshape()) reshape.add(c);
            }
        }
        reshape(level, reshape);
        gate.finished();
        return applied.build();
    }

    private static List<Change> applyGroup(ServerLevel level, List<Change> group, Gate gate) {
        for (Change c : group) {
            if (!level.isLoaded(c.pos()) || level.getBlockState(c.pos()) != c.oldState()) return List.of();
        }
        if (!gate.admit(group)) return List.of();
        for (Change c : group) {
            level.setBlock(c.pos(), c.newState(), FLAGS);
            CompoundTag tag = c.blockEntity();
            if (tag != null) {
                BlockEntity be = level.getBlockEntity(c.pos());
                if (be != null) {
                    be.loadWithComponents(tag.copy(), level.registryAccess());
                    be.setChanged();
                    level.sendBlockUpdated(c.pos(), c.newState(), c.newState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        gate.settled(group);
        return group;
    }

    private static boolean connectable(BlockState s) {
        Block b = s.getBlock();
        return b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock;
    }

    /** Recomputes connections of reshaped blocks and their connectable neighbours. */
    private static void reshape(ServerLevel level, List<Change> changes) {
        if (changes.isEmpty()) return;
        LongLinkedOpenHashSet todo = new LongLinkedOpenHashSet();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Change c : changes) {
            todo.add(c.pos().asLong());
            for (Direction d : Direction.values()) {
                n.setWithOffset(c.pos(), d);
                if (connectable(level.getBlockState(n))) todo.add(n.asLong());
            }
        }
        for (long l : todo) {
            BlockPos p = BlockPos.of(l);
            BlockState s = level.getBlockState(p);
            BlockState u = Block.updateFromNeighbourShapes(s, level, p);
            if (u != s) level.setBlock(p, u, FLAGS);
        }
    }
}
