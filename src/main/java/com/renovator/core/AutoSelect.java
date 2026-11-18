package com.renovator.core;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the building around a block. The flood runs only through {@link BlockKinds#CORE core} blocks (walls, roof,
 * floors under the roof). Loose groups (fences, lanterns, panes, roads, low garden walls ...) join the building only
 * when they lie completely within its core bounds plus a small margin: a lamp post at the door or a window belongs to
 * the house, a road, a field fence or a garden wall that leads away does not. Clicking a loose group that does not
 * hug a building selects just that group (a road, a fence line).
 */
public final class AutoSelect {
    private AutoSelect() {}

    /** How far loose parts may reach beyond the core bounds and still belong to the building. */
    static final int HUG_XZ = 2, HUG_DOWN = 1, HUG_UP = 2;
    /** Face and edge neighbours (no corner-only contact: two buildings touching at a corner stay apart). */
    private static final int[][] N18;

    static {
        int[][] n = new int[18][];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int a = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (a == 0 || a == 3) continue;
                    n[i++] = new int[]{dx, dy, dz};
                }
        N18 = n;
    }

    private static final byte ACCEPTED = 1, REJECTED = 2;

    public static ResolvedSelection flood(BlockGetter level, BlockPos start, int radius, int limit) {
        BlockKinds kinds = new BlockKinds(level);
        BoundingBox within = new BoundingBox(start.getX() - radius, start.getY() - radius, start.getZ() - radius,
                start.getX() + radius, start.getY() + radius, start.getZ() + radius);
        byte k = kinds.kind(start);
        if (k == BlockKinds.NONE) return ResolvedSelection.ofPositions(new LongArrayList(), false);
        Long2ByteOpenHashMap loose = new Long2ByteOpenHashMap();
        LongArrayList startGroup = null;
        LongArrayList core;
        if (k == BlockKinds.LOOSE) {
            // a loose block: the building it hangs on, if the whole group hugs one building; else just the group
            startGroup = new LongArrayList();
            if (!group(kinds, start.asLong(), within, null, limit, loose, startGroup)) return truncated();
            LongArrayList touching = new LongArrayList();
            LongOpenHashSet touchingSet = new LongOpenHashSet();
            for (int i = 0; i < startGroup.size(); i++) {
                forNeighbours(startGroup.getLong(i), within, n -> {
                    if (kinds.kind(n) == BlockKinds.CORE && touchingSet.add(n)) touching.add(n);
                });
            }
            core = null;
            LongOpenHashSet done = new LongOpenHashSet();
            for (int i = 0; i < touching.size() && core == null; i++) {
                if (done.contains(touching.getLong(i))) continue;
                LongArrayList seed = new LongArrayList();
                seed.add(touching.getLong(i));
                LongArrayList c = coreFlood(kinds, seed, within, limit);
                if (c == null) return truncated();
                done.addAll(c);
                if (allInside(startGroup, hugBox(c))) core = c;
            }
            if (core == null) return ResolvedSelection.ofPositions(startGroup, false);
        } else {
            LongArrayList seed = new LongArrayList();
            seed.add(start.asLong());
            core = coreFlood(kinds, seed, within, limit);
            if (core == null) return truncated();
        }
        LongArrayList out = new LongArrayList(core);
        if (startGroup != null) out.addAll(startGroup);
        if (!attach(kinds, core, within, hugBox(core), limit, loose, out)) return truncated();
        return ResolvedSelection.ofPositions(out, false);
    }

    /**
     * The parts of a box that belong to buildings: everything built in it except loose groups that touch a building
     * without hugging it (the road at the door, the garden wall, the field fence). Free-standing things stay.
     */
    public static ResolvedSelection buildingsIn(BlockGetter level, ResolvedSelection box) {
        BlockKinds kinds = new BlockKinds(level);
        BoundingBox within = box.bounds();
        int limit = Integer.MAX_VALUE;
        Long2ByteOpenHashMap loose = new Long2ByteOpenHashMap();
        LongOpenHashSet coreSeen = new LongOpenHashSet();
        LongArrayList out = new LongArrayList();
        box.forEach(p -> {
            long key = p.asLong();
            if (coreSeen.contains(key) || kinds.kind(key) != BlockKinds.CORE) return;
            LongArrayList seed = new LongArrayList();
            seed.add(key);
            LongArrayList core = coreFlood(kinds, seed, within, limit);
            if (core == null) return;
            coreSeen.addAll(core);
            out.addAll(core);
            attach(kinds, core, within, hugBox(core), limit, loose, out);
        });
        box.forEach(p -> {
            long key = p.asLong();
            if (loose.containsKey(key) || kinds.kind(key) != BlockKinds.LOOSE) return;
            LongArrayList g = new LongArrayList();
            // reached by no building above, so it touches none: free-standing, kept
            group(kinds, key, within, null, limit, loose, g);
            out.addAll(g);
        });
        return ResolvedSelection.ofPositions(out, false);
    }

    /** The nearest built block within {@code reach} (the block itself first), or null. */
    public static @Nullable BlockPos nearestBuilt(BlockGetter level, BlockPos at, int reach) {
        BlockKinds kinds = new BlockKinds(level);
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach))) {
            int d = p.distManhattan(at);
            if (d < bestD && kinds.kind(p) != BlockKinds.NONE) {
                best = p.immutable();
                bestD = d;
            }
        }
        return best;
    }

    private static ResolvedSelection truncated() {
        return ResolvedSelection.ofPositions(new LongArrayList(), true);
    }

    private interface LongSink {
        void accept(long key);
    }

    private static void forNeighbours(long key, BoundingBox within, LongSink sink) {
        int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
        for (int[] d : N18) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (nx < within.minX() || nx > within.maxX() || ny < within.minY() || ny > within.maxY() || nz < within.minZ() || nz > within.maxZ()) continue;
            sink.accept(BlockPos.asLong(nx, ny, nz));
        }
    }

    /** Connected core blocks from the seeds; null when more than {@code limit}. */
    private static @Nullable LongArrayList coreFlood(BlockKinds kinds, LongArrayList seeds, BoundingBox within, int limit) {
        LongOpenHashSet seen = new LongOpenHashSet(seeds);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int i = 0; i < seeds.size(); i++) queue.enqueue(seeds.getLong(i));
        LongArrayList found = new LongArrayList();
        while (!queue.isEmpty()) {
            long cur = queue.dequeueLong();
            found.add(cur);
            if (found.size() > limit) return null;
            forNeighbours(cur, within, n -> {
                if (!seen.add(n)) return;
                if (kinds.kind(n) == BlockKinds.CORE) queue.enqueue(n);
            });
        }
        return found;
    }

    /** Adds the loose groups hanging on the core that hug it. False when the selection grows over the limit. */
    private static boolean attach(BlockKinds kinds, LongArrayList core, BoundingBox within, BoundingBox hug, int limit,
                                  Long2ByteOpenHashMap loose, LongArrayList out) {
        for (int i = 0; i < core.size(); i++) {
            long[] next = new long[18];
            int[] count = {0};
            forNeighbours(core.getLong(i), within, n -> {
                if (!loose.containsKey(n) && kinds.kind(n) == BlockKinds.LOOSE) next[count[0]++] = n;
            });
            for (int j = 0; j < count[0]; j++) {
                if (loose.containsKey(next[j])) continue;
                LongArrayList g = new LongArrayList();
                if (group(kinds, next[j], within, hug, limit, loose, g)) {
                    out.addAll(g);
                    if (out.size() > limit) return false;
                }
            }
        }
        return true;
    }

    /**
     * The connected loose group at {@code start}, written to {@code out}. With a {@code hug} box the group is rejected
     * (false, out cleared) as soon as it leaves the box or meets a group rejected before; without one it is rejected
     * only above {@code limit}. Every visited block is marked accepted or rejected in {@code state}.
     */
    private static boolean group(BlockKinds kinds, long start, BoundingBox within, @Nullable BoundingBox hug, int limit,
                                 Long2ByteOpenHashMap state, LongArrayList out) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start);
        queue.enqueue(start);
        boolean ok = true;
        while (!queue.isEmpty() && ok) {
            long cur = queue.dequeueLong();
            out.add(cur);
            if (out.size() > limit || (hug != null && !hug.isInside(BlockPos.getX(cur), BlockPos.getY(cur), BlockPos.getZ(cur)))) {
                ok = false;
                break;
            }
            boolean[] bad = {false};
            forNeighbours(cur, within, n -> {
                if (bad[0] || seen.contains(n)) return;
                byte st = state.get(n);
                if (st == REJECTED) {
                    bad[0] = true;
                    return;
                }
                if (kinds.kind(n) != BlockKinds.LOOSE) return;
                seen.add(n);
                queue.enqueue(n);
            });
            if (bad[0]) ok = false;
        }
        byte mark = ok ? ACCEPTED : REJECTED;
        for (long s : seen) state.put(s, mark);
        if (!ok) out.clear();
        return ok;
    }

    private static BoundingBox hugBox(LongArrayList core) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < core.size(); i++) {
            long p = core.getLong(i);
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return new BoundingBox(minX - HUG_XZ, minY - HUG_DOWN, minZ - HUG_XZ, maxX + HUG_XZ, maxY + HUG_UP, maxZ + HUG_XZ);
    }

    private static boolean allInside(LongArrayList positions, BoundingBox box) {
        for (int i = 0; i < positions.size(); i++) {
            long p = positions.getLong(i);
            if (!box.isInside(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) return false;
        }
        return true;
    }
}
