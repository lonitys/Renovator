package com.stilwerk.net;

import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintPlanner;
import com.stilwerk.blueprint.Transform;
import com.stilwerk.config.ServerConfig;
import com.stilwerk.core.Applier;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.CreativeGate;
import com.stilwerk.core.Gate;
import com.stilwerk.core.MaterialLedger;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import com.stilwerk.core.UndoHistory;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.net.SmallPayloads.ConfirmRequest;
import com.stilwerk.net.SmallPayloads.Line;
import com.stilwerk.net.SmallPayloads.MaterialReport;
import com.stilwerk.swap.SwapPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side of every operation: validate → plan → (survival) report/confirm → apply → remember for undo. */
public final class ServerOps {
    private ServerOps() {}

    /** How far from the player an operation may reach. */
    public static final int MAX_DISTANCE = 256;

    public record Pending(int id, ServerLevel level, ChangeSet set, MaterialLedger ledger) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, String> LAST_MESSAGE = new HashMap<>();

    public static synchronized Map<UUID, Pending> pendingForTests() {
        return PENDING;
    }

    public static synchronized String lastMessageForTests(UUID player) {
        return LAST_MESSAGE.get(player);
    }

    private static synchronized void tell(ServerPlayer p, String key, Object... args) {
        LAST_MESSAGE.put(p.getUUID(), key);
        p.sendSystemMessage(Component.translatable(key, args));
    }

    private static boolean nearby(ServerPlayer p, BoundingBox bb) {
        double cx = (bb.minX() + bb.maxX()) / 2.0, cy = (bb.minY() + bb.maxY()) / 2.0, cz = (bb.minZ() + bb.maxZ()) / 2.0;
        double reach = MAX_DISTANCE + Math.max(bb.getXSpan(), Math.max(bb.getYSpan(), bb.getZSpan())) / 2.0;
        return p.distanceToSqr(cx, cy, cz) <= reach * reach;
    }

    private static boolean loaded(ServerLevel level, BoundingBox bb) {
        return level.hasChunksAt(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ());
    }

    private static Map<String, MixPalette> mixes(List<MixPalette> list) {
        Map<String, MixPalette> m = new LinkedHashMap<>();
        for (MixPalette p : list) m.put(p.id(), p);
        return m;
    }

    public static void swap(ServerPlayer p, SwapRequest r) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "stilwerk.error.busy");
            return;
        }
        boolean creative = p.isCreative();
        int limit = ServerConfig.maxBlocks(creative);
        ServerLevel level = p.serverLevel();
        Selection sel = r.selection();
        if (sel instanceof Selection.Flood f && f.radius() > ServerConfig.floodRadius()) {
            sel = new Selection.Flood(f.start(), ServerConfig.floodRadius());
        }
        if (sel instanceof Selection.Box box) {
            BoundingBox bb = box.bounds();
            if ((long) bb.getXSpan() * bb.getYSpan() * bb.getZSpan() > limit) {
                tell(p, "stilwerk.error.too_big", limit);
                return;
            }
            if (!nearby(p, bb)) {
                tell(p, "stilwerk.error.too_far");
                return;
            }
            if (!loaded(level, bb)) {
                tell(p, "stilwerk.error.unloaded");
                return;
            }
        } else if (!level.isLoaded(((Selection.Flood) sel).start()) || !nearby(p, new BoundingBox(((Selection.Flood) sel).start()))) {
            tell(p, "stilwerk.error.too_far");
            return;
        }
        ResolvedSelection res = sel.resolve(level, limit);
        if (res.truncated()) {
            tell(p, "stilwerk.error.too_big", limit);
            return;
        }
        if (!loaded(level, res.bounds())) {
            tell(p, "stilwerk.error.unloaded");
            return;
        }
        SwapPlanner.Result plan = SwapPlanner.plan(level, res, r.mapping(), mixes(r.mixes()), r.seed(),
                new SwapPlanner.Options(r.includeNature(), !creative));
        submit(p, level, plan.changes(), r.id(), r.partialOk(), centre(res.bounds()));
    }

    public static void place(ServerPlayer p, PlaceRequest r) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "stilwerk.error.busy");
            return;
        }
        Blueprint bp = UploadBuffer.get(p, r.hash());
        if (bp == null) {
            tell(p, "stilwerk.error.missing_blueprint");
            return;
        }
        boolean creative = p.isCreative();
        int limit = ServerConfig.maxBlocks(creative);
        Blueprint t = Transform.apply(bp, r.rotation(), r.mirror());
        if (t.nonAir() > limit) {
            tell(p, "stilwerk.error.too_big", limit);
            return;
        }
        Vec3i s = t.size();
        BoundingBox bb = new BoundingBox(r.origin().getX(), r.origin().getY(), r.origin().getZ(),
                r.origin().getX() + s.getX() - 1, r.origin().getY() + s.getY() - 1, r.origin().getZ() + s.getZ() - 1);
        ServerLevel level = p.serverLevel();
        if (!nearby(p, bb)) {
            tell(p, "stilwerk.error.too_far");
            return;
        }
        if (bb.minY() < level.getMinBuildHeight() || bb.maxY() >= level.getMaxBuildHeight() || !loaded(level, bb)) {
            tell(p, "stilwerk.error.unloaded");
            return;
        }
        BlueprintPlanner.Result plan = BlueprintPlanner.plan(level, t, r.origin(), r.mapping(), mixes(r.mixes()), r.seed(),
                new BlueprintPlanner.Options(r.overwrite() && creative, !creative));
        if (plan.blocked() > 0) tell(p, "stilwerk.info.blocked", plan.blocked());
        submit(p, level, plan.changes(), r.id(), r.partialOk(), centre(bb));
    }

    private static BlockPos centre(BoundingBox bb) {
        return new BlockPos((bb.minX() + bb.maxX()) / 2, bb.maxY() + 1, (bb.minZ() + bb.maxZ()) / 2);
    }

    private static void submit(ServerPlayer p, ServerLevel level, ChangeSet set, int id, boolean partialOk, BlockPos centre) {
        if (set.isEmpty()) {
            tell(p, "stilwerk.info.nothing");
            return;
        }
        if (p.isCreative()) {
            run(p, level, set, CreativeGate.INSTANCE);
            return;
        }
        MaterialLedger ledger = new MaterialLedger(p, centre, ServerConfig.useProjectE());
        MaterialLedger.Report report = ledger.check(set);
        if (report.complete() || partialOk) {
            run(p, level, set, ledger);
            return;
        }
        synchronized (ServerOps.class) {
            PENDING.put(p.getUUID(), new Pending(id, level, set, ledger));
        }
        List<Line> lines = new ArrayList<>();
        for (MaterialLedger.Line l : report.lines()) lines.add(new Line(l.item(), l.need(), l.inInventory(), l.missing(), l.viaEmc()));
        PacketDistributor.sendToPlayer(p, new MaterialReport(id, lines, false));
        tell(p, "stilwerk.info.missing_materials");
    }

    public static void confirm(ServerPlayer p, ConfirmRequest r) {
        Pending pending;
        synchronized (ServerOps.class) {
            pending = PENDING.get(p.getUUID());
            if (pending == null || pending.id() != r.id()) return;
            PENDING.remove(p.getUUID());
        }
        if (!r.proceed()) {
            tell(p, "stilwerk.info.cancelled");
            return;
        }
        if (Applier.busy(p.getUUID())) {
            tell(p, "stilwerk.error.busy");
            return;
        }
        run(p, pending.level(), pending.set(), pending.ledger());
    }

    private static void run(ServerPlayer p, ServerLevel level, ChangeSet set, Gate gate) {
        UUID id = p.getUUID();
        Applier.submit(new Applier.Job(id, level, set, gate, applied -> {
            UndoHistory.push(level.getServer(), id, new UndoHistory.Entry(level.dimension(), applied, System.currentTimeMillis()));
            tell(p, "stilwerk.info.done", applied.size());
        }, p));
    }

    public static void undo(ServerPlayer p) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "stilwerk.error.busy");
            return;
        }
        var entry = UndoHistory.pop(p.server, p.getUUID());
        if (entry.isEmpty()) {
            tell(p, "stilwerk.info.undo_empty");
            return;
        }
        ServerLevel level = p.server.getLevel(entry.get().dimension());
        if (level == null) {
            tell(p, "stilwerk.error.unloaded");
            return;
        }
        ChangeSet inverse = entry.get().applied().inverse();
        BlockPos anchor = inverse.changes().get(0).pos().above();
        Gate gate = p.isCreative() ? CreativeGate.INSTANCE : new MaterialLedger(p, anchor, ServerConfig.useProjectE());
        Applier.submit(new Applier.Job(p.getUUID(), level, inverse, gate,
                applied -> tell(p, "stilwerk.info.undone", applied.size()), p));
    }

    /** Forget everything about a player who left. */
    public static synchronized void forget(UUID player) {
        PENDING.remove(player);
        LAST_MESSAGE.remove(player);
        UploadBuffer.forget(player);
    }
}
