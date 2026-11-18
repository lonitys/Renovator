package com.renovator.net;

import com.renovator.blueprint.Blueprint;
import com.renovator.blueprint.BlueprintPlanner;
import com.renovator.blueprint.Transform;
import com.renovator.config.ServerConfig;
import com.renovator.core.Applier;
import com.renovator.core.ChangeSet;
import com.renovator.core.CreativeGate;
import com.renovator.core.Gate;
import com.renovator.core.MaterialLedger;
import com.renovator.core.ResolvedSelection;
import com.renovator.core.Selection;
import com.renovator.core.UndoHistory;
import com.renovator.mix.MixPalette;
import com.renovator.net.SmallPayloads.ConfirmRequest;
import com.renovator.net.SmallPayloads.Line;
import com.renovator.net.SmallPayloads.MaterialReport;
import com.renovator.swap.SwapPlanner;
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

    public record Pending(int id, ServerLevel level, ChangeSet set, MaterialLedger ledger, String labelKey, String labelArg) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, String> LAST_MESSAGE = new HashMap<>();

    public static synchronized Map<UUID, Pending> pendingForTests() {
        return PENDING;
    }

    public static synchronized String lastMessageForTests(UUID player) {
        return LAST_MESSAGE.get(player);
    }

    public static synchronized void tell(ServerPlayer p, String key, Object... args) {
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
            tell(p, "renovator.error.busy");
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
                tell(p, "renovator.error.too_big", limit);
                return;
            }
            if (!nearby(p, bb)) {
                tell(p, "renovator.error.too_far");
                return;
            }
            if (!loaded(level, bb)) {
                tell(p, "renovator.error.unloaded");
                return;
            }
        } else if (!level.isLoaded(((Selection.Flood) sel).start()) || !nearby(p, new BoundingBox(((Selection.Flood) sel).start()))) {
            tell(p, "renovator.error.too_far");
            return;
        }
        ResolvedSelection res = sel.resolve(level, limit);
        if (res.truncated()) {
            tell(p, "renovator.error.too_big", limit);
            return;
        }
        if (!loaded(level, res.bounds())) {
            tell(p, "renovator.error.unloaded");
            return;
        }
        SwapPlanner.Result plan = SwapPlanner.plan(level, res, r.mapping(), mixes(r.mixes()), r.seed(),
                new SwapPlanner.Options(r.includeNature(), !creative));
        submit(p, level, plan.changes(), r.id(), r.mode(), centre(res.bounds()), "renovator.projection.label.swap", "");
    }

    public static void place(ServerPlayer p, PlaceRequest r) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "renovator.error.busy");
            return;
        }
        Blueprint bp = UploadBuffer.get(p, r.hash());
        if (bp == null) {
            tell(p, "renovator.error.missing_blueprint");
            return;
        }
        boolean creative = p.isCreative();
        int limit = ServerConfig.maxBlocks(creative);
        Blueprint t = Transform.apply(bp, r.rotation(), r.mirror());
        if (t.nonAir() > limit) {
            tell(p, "renovator.error.too_big", limit);
            return;
        }
        Vec3i s = t.size();
        BoundingBox bb = new BoundingBox(r.origin().getX(), r.origin().getY(), r.origin().getZ(),
                r.origin().getX() + s.getX() - 1, r.origin().getY() + s.getY() - 1, r.origin().getZ() + s.getZ() - 1);
        ServerLevel level = p.serverLevel();
        if (!nearby(p, bb)) {
            tell(p, "renovator.error.too_far");
            return;
        }
        if (bb.minY() < level.getMinBuildHeight() || bb.maxY() >= level.getMaxBuildHeight() || !loaded(level, bb)) {
            tell(p, "renovator.error.unloaded");
            return;
        }
        BlueprintPlanner.Result plan = BlueprintPlanner.plan(level, t, r.origin(), r.mapping(), mixes(r.mixes()), r.seed(),
                new BlueprintPlanner.Options(r.overwrite() && creative, !creative));
        if (plan.blocked() > 0) tell(p, "renovator.info.blocked", plan.blocked());
        submit(p, level, plan.changes(), r.id(), r.mode(), centre(bb), "renovator.projection.label.blueprint", bp.meta().name());
    }

    /** Detail suggestions: recomputed here from the selection (or around the clicked block), chosen variants applied. */
    public static void detail(ServerPlayer p, DetailRequest r) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "renovator.error.busy");
            return;
        }
        boolean creative = p.isCreative();
        int limit = ServerConfig.maxBlocks(creative);
        ServerLevel level = p.serverLevel();
        java.util.List<com.renovator.detail.DetailTypes.Suggestion> all;
        BoundingBox bb;
        if (r.selection().isPresent()) {
            Selection sel = r.selection().get();
            if (sel instanceof Selection.Flood f && f.radius() > ServerConfig.floodRadius()) sel = new Selection.Flood(f.start(), ServerConfig.floodRadius());
            BoundingBox rough = sel.roughBounds();
            if (sel instanceof Selection.Box && (long) rough.getXSpan() * rough.getYSpan() * rough.getZSpan() > limit) {
                tell(p, "renovator.error.too_big", limit);
                return;
            }
            if (!nearby(p, rough) || !loaded(level, rough.inflatedBy(2))) {
                tell(p, "renovator.error.unloaded");
                return;
            }
            ResolvedSelection res = sel.resolve(level, limit);
            if (res.truncated()) {
                tell(p, "renovator.error.too_big", limit);
                return;
            }
            bb = res.bounds();
            all = com.renovator.detail.DetailPlanner.find(level, res, r.seed());
        } else if (r.clicked().isPresent()) {
            // the building detection stops at unloaded chunks; only the clicked spot itself must be loaded
            bb = new BoundingBox(r.clicked().get()).inflatedBy(com.renovator.detail.DetailPlanner.NEAR_PICK + com.renovator.detail.DetailPlanner.NEAR_REACH);
            if (!nearby(p, bb) || !loaded(level, bb)) {
                tell(p, "renovator.error.unloaded");
                return;
            }
            all = com.renovator.detail.DetailPlanner.near(level, r.clicked().get(), r.seed());
        } else {
            return;
        }
        ChangeSet set = com.renovator.detail.DetailPlanner.combine(all, r.choices());
        if (set.size() > limit) {
            tell(p, "renovator.error.too_big", limit);
            return;
        }
        submit(p, level, set, r.id(), r.mode(), centre(bb), "renovator.projection.label.details", "");
    }

    /** Interior: the room is detected again here and furnished with the same type and seed as the client preview. */
    public static void interior(ServerPlayer p, InteriorRequest r) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "renovator.error.busy");
            return;
        }
        ServerLevel level = p.serverLevel();
        BoundingBox around = new BoundingBox(r.clicked()).inflatedBy(com.renovator.interior.RoomDetector.MAX_SPAN_XZ);
        if (!nearby(p, new BoundingBox(r.clicked())) || !loaded(level, around)) {
            tell(p, "renovator.error.unloaded");
            return;
        }
        var detected = com.renovator.interior.RoomDetector.detect(level, r.clicked(), r.face());
        if (detected.room() == null) {
            tell(p, detected.error());
            return;
        }
        var room = detected.room();
        var type = com.renovator.interior.ModuleLibrary.type(r.roomType());
        var result = com.renovator.interior.InteriorGenerator.generate(level, room, type, r.seed(),
                com.renovator.interior.InteriorGenerator.houseWood(level, room), r.furniture());
        int limit = ServerConfig.maxBlocks(p.isCreative());
        if (result.changes().size() > limit) {
            tell(p, "renovator.error.too_big", limit);
            return;
        }
        submit(p, level, result.changes(), r.id(), r.mode(), room.start(), "renovator.projection.label.interior", type.translationKey());
    }

    private static BlockPos centre(BoundingBox bb) {
        return new BlockPos((bb.minX() + bb.maxX()) / 2, bb.maxY() + 1, (bb.minZ() + bb.maxZ()) / 2);
    }

    /**
     * Planned changes: kept as a projection, built at once (creative, or {@link BuildMode#PARTIAL}), or - survival with
     * {@link BuildMode#ASK} - held while the player answers the build dialog.
     */
    private static void submit(ServerPlayer p, ServerLevel level, ChangeSet set, int id, BuildMode mode, BlockPos centre,
                               String labelKey, String labelArg) {
        if (set.isEmpty()) {
            tell(p, "renovator.info.nothing");
            return;
        }
        if (mode == BuildMode.PROJECTION) {
            com.renovator.projection.ProjectionOps.create(p, level, set, labelKey, labelArg);
            return;
        }
        if (p.isCreative()) {
            run(p, level, set, CreativeGate.INSTANCE);
            return;
        }
        MaterialLedger ledger = new MaterialLedger(p, centre, ServerConfig.useProjectE());
        if (mode == BuildMode.PARTIAL) {
            run(p, level, set, ledger);
            return;
        }
        MaterialLedger.Report report = ledger.check(set);
        synchronized (ServerOps.class) {
            PENDING.put(p.getUUID(), new Pending(id, level, set, ledger, labelKey, labelArg));
        }
        List<Line> lines = new ArrayList<>();
        for (MaterialLedger.Line l : report.lines()) lines.add(new Line(l.item(), l.need(), l.inInventory(), l.missing(), l.viaEmc()));
        PacketDistributor.sendToPlayer(p, new MaterialReport(id, lines, report.complete()));
        if (!report.complete()) tell(p, "renovator.info.missing_materials");
    }

    public static void confirm(ServerPlayer p, ConfirmRequest r) {
        Pending pending;
        synchronized (ServerOps.class) {
            pending = PENDING.get(p.getUUID());
            if (pending == null || pending.id() != r.id()) return;
            PENDING.remove(p.getUUID());
        }
        switch (r.action()) {
            case CANCEL -> tell(p, "renovator.info.cancelled");
            case PROJECT -> com.renovator.projection.ProjectionOps.create(p, pending.level(), pending.set(), pending.labelKey(), pending.labelArg());
            case BUILD -> {
                if (Applier.busy(p.getUUID())) {
                    tell(p, "renovator.error.busy");
                    return;
                }
                run(p, pending.level(), pending.set(), pending.ledger());
            }
        }
    }

    private static void run(ServerPlayer p, ServerLevel level, ChangeSet set, Gate gate) {
        UUID id = p.getUUID();
        Applier.submit(new Applier.Job(id, level, set, gate, applied -> {
            UndoHistory.push(level.getServer(), id, new UndoHistory.Entry(level.dimension(), applied, System.currentTimeMillis()));
            tell(p, "renovator.info.done", applied.size());
        }, p));
    }

    public static void undo(ServerPlayer p) {
        if (Applier.busy(p.getUUID())) {
            tell(p, "renovator.error.busy");
            return;
        }
        var entry = UndoHistory.pop(p.server, p.getUUID());
        if (entry.isEmpty()) {
            tell(p, "renovator.info.undo_empty");
            return;
        }
        ServerLevel level = p.server.getLevel(entry.get().dimension());
        if (level == null) {
            tell(p, "renovator.error.unloaded");
            return;
        }
        ChangeSet inverse = entry.get().applied().inverse();
        BlockPos anchor = inverse.changes().get(0).pos().above();
        Gate gate = p.isCreative() ? CreativeGate.INSTANCE : new MaterialLedger(p, anchor, ServerConfig.useProjectE());
        Applier.submit(new Applier.Job(p.getUUID(), level, inverse, gate,
                applied -> tell(p, "renovator.info.undone", applied.size()), p));
    }

    /** Forget everything about a player who left. */
    public static synchronized void forget(UUID player) {
        PENDING.remove(player);
        LAST_MESSAGE.remove(player);
        UploadBuffer.forget(player);
    }
}
