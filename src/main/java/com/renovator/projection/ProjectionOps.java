package com.renovator.projection;

import com.renovator.config.ServerConfig;
import com.renovator.core.Applier;
import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.core.CreativeGate;
import com.renovator.core.EmcBank;
import com.renovator.core.Gate;
import com.renovator.core.MaterialLedger;
import com.renovator.core.UndoHistory;
import com.renovator.net.ProjectionPayloads.Build;
import com.renovator.net.ProjectionPayloads.Place;
import com.renovator.net.ProjectionPayloads.ProjAction;
import com.renovator.net.ProjectionPayloads.Removed;
import com.renovator.net.ProjectionPayloads.Scope;
import com.renovator.net.ProjectionPayloads.Sync;
import com.renovator.net.ServerOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Server side of the build projection: create, build by hand, build automatically, delete, finish. */
public final class ProjectionOps {
    private ProjectionOps() {}

    /** Shown as "Blueprint: house", "Interior: bedroom" ...; a translation key as argument is translated. */
    public static Component label(String labelKey, String labelArg) {
        Component arg = labelArg.startsWith("renovator.") ? Component.translatable(labelArg) : Component.literal(labelArg);
        return Component.translatable(labelKey, arg);
    }

    public static Component label(Projection p) {
        return label(p.labelKey(), p.labelArg());
    }

    public static Projection create(ServerPlayer p, ServerLevel level, ChangeSet set, String labelKey, String labelArg) {
        Projection pr = Projection.of(set, labelKey, labelArg, level.dimension());
        for (Projection dropped : ProjectionStore.add(level.getServer(), p.getUUID(), pr)) send(p, new Removed(dropped.id()));
        sync(p, pr);
        ServerOps.tell(p, "renovator.projection.created", label(pr), pr.cellCount());
        return pr;
    }

    /** Sends all projections of the player (login). */
    public static void syncAll(ServerPlayer p) {
        for (Projection pr : ProjectionStore.list(p.server, p.getUUID())) sync(p, pr);
    }

    static void sync(ServerPlayer p, Projection pr) {
        List<Change> cells = new ArrayList<>();
        List<Integer> groupOf = new ArrayList<>();
        List<List<Change>> groups = pr.set().groups();
        for (int g = 0; g < groups.size(); g++)
            for (Change c : groups.get(g)) {
                cells.add(c);
                groupOf.add(g);
            }
        int parts = Math.max(1, (cells.size() + com.renovator.net.ProjectionPayloads.PART - 1) / com.renovator.net.ProjectionPayloads.PART);
        for (int part = 0; part < parts; part++) {
            int from = part * com.renovator.net.ProjectionPayloads.PART, to = Math.min(cells.size(), from + com.renovator.net.ProjectionPayloads.PART);
            int n = to - from;
            long[] positions = new long[n];
            int[] targets = new int[n], olds = new int[n], gs = new int[n];
            for (int i = 0; i < n; i++) {
                Change c = cells.get(from + i);
                positions[i] = c.pos().asLong();
                targets[i] = Block.getId(c.newState());
                olds[i] = Block.getId(c.oldState());
                gs[i] = groupOf.get(from + i);
            }
            send(p, new Sync(pr.id(), pr.labelKey(), pr.labelArg(), pr.dimension().location(), part, parts, positions, targets, olds, gs));
        }
    }

    private static void send(ServerPlayer p, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (p.connection != null) PacketDistributor.sendToPlayer(p, payload);
    }

    /** The open cells of a group with the world's current states as old states; null when a cell is blocked. */
    static @Nullable List<Change> fresh(ServerLevel level, List<Change> group) {
        List<Change> out = new ArrayList<>();
        for (Change c : group) {
            if (!level.isLoaded(c.pos())) return null;
            BlockState w = level.getBlockState(c.pos());
            CellState st = CellState.of(w, c);
            if (st == CellState.BLOCKED) return null;
            if (st == CellState.OPEN) out.add(new Change(c.pos(), w, c.newState(), c.blockEntity(), c.reshape()));
        }
        return out;
    }

    static @Nullable List<Change> groupAt(Projection pr, BlockPos pos) {
        for (List<Change> g : pr.set().groups()) for (Change c : g) if (c.pos().equals(pos)) return g;
        return null;
    }

    private static Optional<Projection> find(ServerPlayer p, UUID id) {
        return ProjectionStore.get(p.server, p.getUUID(), id);
    }

    /** Builds the group of one cell by hand: exact state, paid from the inventory (survival), old blocks given back. */
    public static void place(ServerPlayer p, Place r) {
        Projection pr = find(p, r.id()).orElse(null);
        if (pr == null || !p.serverLevel().dimension().equals(pr.dimension())) return;
        ServerLevel level = p.serverLevel();
        if (!p.canInteractWithBlock(r.pos(), 1.0)) {
            ServerOps.tell(p, "renovator.error.too_far");
            return;
        }
        List<Change> group = groupAt(pr, r.pos());
        if (group == null) return;
        List<Change> fresh = fresh(level, group);
        if (fresh == null) {
            ServerOps.tell(p, "renovator.projection.blocked");
            return;
        }
        if (fresh.isEmpty()) {
            checkDone(p, pr);
            return;
        }
        Gate gate = p.isCreative() ? CreativeGate.INSTANCE : new MaterialLedger(p, r.pos(), (EmcBank) null);
        ChangeSet applied = Applier.applyNow(level, ChangeSet.builder().group(fresh).build(), gate);
        if (applied.isEmpty()) {
            ServerOps.tell(p, "renovator.projection.need", fresh.get(0).newState().getBlock().getName());
            return;
        }
        BlockState placed = fresh.get(0).newState();
        SoundType sound = placed.getSoundType(level, r.pos(), p);
        level.playSound(null, r.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        checkDone(p, pr);
    }

    static boolean inScope(List<Change> group, Scope scope, int y) {
        int min = Integer.MAX_VALUE;
        for (Change c : group) min = Math.min(min, c.pos().getY());
        return switch (scope) {
            case LAYER -> min == y;
            case UP_TO -> min <= y;
            case ALL -> true;
        };
    }

    /** Builds the open groups in scope automatically; what cannot be paid for stays a ghost. One undo step. */
    public static void build(ServerPlayer p, Build r) {
        Projection pr = find(p, r.id()).orElse(null);
        if (pr == null) return;
        ServerLevel level = p.serverLevel();
        if (!level.dimension().equals(pr.dimension())) {
            ServerOps.tell(p, "renovator.error.too_far");
            return;
        }
        if (Applier.busy(p.getUUID())) {
            ServerOps.tell(p, "renovator.error.busy");
            return;
        }
        ChangeSet.Builder b = ChangeSet.builder();
        for (List<Change> g : pr.set().groups()) {
            if (!inScope(g, r.scope(), r.y())) continue;
            List<Change> fresh = fresh(level, g);
            if (fresh != null && !fresh.isEmpty()) b.group(fresh);
        }
        ChangeSet set = b.build();
        if (set.isEmpty()) {
            ServerOps.tell(p, "renovator.info.nothing");
            checkDone(p, pr);
            return;
        }
        Gate gate = p.isCreative() ? CreativeGate.INSTANCE : new MaterialLedger(p, set.changes().get(0).pos(), ServerConfig.useProjectE());
        UUID owner = p.getUUID();
        Applier.submit(new Applier.Job(owner, level, set, gate, applied -> {
            UndoHistory.push(level.getServer(), owner, new UndoHistory.Entry(level.dimension(), applied, System.currentTimeMillis()));
            ServerOps.tell(p, "renovator.info.done", applied.size());
            if (applied.size() < set.size()) ServerOps.tell(p, "renovator.projection.partly", set.size() - applied.size());
            checkDone(p, pr);
        }, p));
    }

    public static void action(ServerPlayer p, ProjAction r) {
        Projection pr = find(p, r.id()).orElse(null);
        if (pr == null) {
            send(p, new Removed(r.id()));
            return;
        }
        switch (r.action()) {
            case DELETE -> {
                ProjectionStore.remove(p.server, p.getUUID(), pr.id());
                send(p, new Removed(pr.id()));
                ServerOps.tell(p, "renovator.projection.deleted", label(pr));
            }
            case REMOVE_IF_DONE -> checkDone(p, pr);
        }
    }

    /** Removes the projection when every cell is built. */
    public static boolean checkDone(ServerPlayer p, Projection pr) {
        ServerLevel level = p.server.getLevel(pr.dimension());
        if (level == null) return false;
        for (Change c : pr.set().changes()) {
            if (!level.isLoaded(c.pos()) || CellState.of(level.getBlockState(c.pos()), c) != CellState.DONE) return false;
        }
        if (ProjectionStore.remove(p.server, p.getUUID(), pr.id())) {
            send(p, new Removed(pr.id()));
            ServerOps.tell(p, "renovator.projection.finished", label(pr));
        }
        return true;
    }
}
