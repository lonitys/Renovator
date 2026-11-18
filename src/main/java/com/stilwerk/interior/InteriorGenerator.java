package com.stilwerk.interior;

import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.Groups;
import com.stilwerk.family.Family;
import com.stilwerk.mix.Hash;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Furnishes a room: furniture groups by priority at their best free spot (wall/corner/window/centre rules,
 * door clearance, walkable floor kept connected), then a rug and a ceiling light. Deterministic per seed.
 */
public final class InteriorGenerator {
    private InteriorGenerator() {}

    public record Result(ChangeSet changes, List<String> placed, DyeColor color) {}

    private static final DyeColor[] COLORS = {DyeColor.RED, DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.BLUE, DyeColor.GREEN,
            DyeColor.BROWN, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.LIGHT_BLUE, DyeColor.LIME};
    private static final Rotation[] ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
    private static final int TRIES = 40;

    private record Candidate(Module module, Rotation rot, Blueprint bp, BlockPos origin, double score, LongArrayList footprint) {}

    private static final class State {
        final BlockGetter level;
        final Room room;
        final long seed;
        final Family wood;
        final DyeColor color;
        final LongOpenHashSet taken = new LongOpenHashSet();       // any cell used by a module
        final LongOpenHashSet takenFloor = new LongOpenHashSet();  // floor cells under modules
        final Long2ObjectLinkedOpenHashMap<BlockState> out = new Long2ObjectLinkedOpenHashMap<>();
        final List<String> placed = new ArrayList<>();
        final double cx, cz;
        final long[] floorSorted;

        State(BlockGetter level, Room room, long seed, Family wood) {
            this.level = level;
            this.room = room;
            this.seed = seed;
            this.wood = wood;
            this.color = COLORS[(int) Math.floorMod(Hash.mix(seed ^ 0x5EEDL), (long) COLORS.length)];
            double sx = 0, sz = 0;
            for (long k : room.floor()) {
                sx += BlockPos.getX(k);
                sz += BlockPos.getZ(k);
            }
            cx = sx / room.floor().size();
            cz = sz / room.floor().size();
            floorSorted = room.floor().toLongArray();
            java.util.Arrays.sort(floorSorted);
            // existing non-air cells on the floor (carpets ...) count as taken
            for (long k : floorSorted) {
                if (!level.getBlockState(BlockPos.of(k)).isAir()) {
                    taken.add(k);
                    takenFloor.add(k);
                }
            }
        }

        boolean freeFloor(long k) {
            return room.floor().contains(k) && !takenFloor.contains(k);
        }
    }

    public static Result generate(BlockGetter level, Room room, RoomType type, long seed, Family wood) {
        State st = new State(level, room, seed, wood);
        List<RoomType.Slot> slots = new ArrayList<>(type.slots());
        slots.sort(Comparator.comparingInt(RoomType.Slot::priority).reversed());
        for (int si = 0; si < slots.size(); si++) {
            RoomType.Slot slot = slots.get(si);
            List<Module> all = ModuleLibrary.available(slot.category());
            if (all.isEmpty()) continue;
            List<Module> pool = all.stream().filter(Module::modded).toList();
            if (pool.isEmpty()) pool = all;
            int span = slot.max() - slot.min() + 1;
            int count = slot.min() + (int) Math.floorMod(Hash.mix(seed + 977L * si), (long) span);
            for (int i = 0; i < count; i++) {
                Candidate c = best(st, pool, slot, si * 31 + i);
                if (c == null) {
                    // a modded group may not fit where a vanilla one does
                    if (pool != all) c = best(st, all, slot, si * 31 + i + 7);
                    if (c == null) break;
                }
                place(st, c);
            }
        }
        if (type.rug()) rug(st);
        if (type.light()) light(st);
        return new Result(build(st), st.placed, st.color);
    }

    private static Direction back(Rotation rot) {
        return rot.rotate(Direction.NORTH);
    }

    private static Candidate best(State st, List<Module> pool, RoomType.Slot slot, int salt) {
        List<Candidate> cands = new ArrayList<>();
        for (Module m : pool) {
            for (Rotation rot : ROTATIONS) {
                Blueprint bp = m.blueprint(st.color, st.wood, rot);
                for (long k : st.floorSorted) {
                    Candidate c = fit(st, m, rot, bp, BlockPos.of(k), slot, salt);
                    if (c != null) cands.add(c);
                }
            }
        }
        cands.sort(Comparator.comparingDouble(Candidate::score).reversed());
        for (int i = 0; i < Math.min(TRIES, cands.size()); i++) {
            if (walkable(st, cands.get(i).footprint())) return cands.get(i);
        }
        return null;
    }

    private static Candidate fit(State st, Module m, Rotation rot, Blueprint bp, BlockPos o, RoomType.Slot slot, int salt) {
        Room room = st.room;
        Vec3i s = bp.size();
        LongArrayList footprint = new LongArrayList();
        Direction w = back(rot);
        boolean window = false;
        int backCells = 0, backWalls = 0;
        for (int x = 0; x < s.getX(); x++)
            for (int z = 0; z < s.getZ(); z++) {
                BlockPos f = o.offset(x, 0, z);
                long fk = f.asLong();
                if (!st.freeFloor(fk) || room.doorClear().contains(fk)) return null;
                footprint.add(fk);
                EnumSet<Direction> walls = room.wallsAt(fk);
                boolean onBack = !inside(s, x + w.getStepX(), z + w.getStepZ());
                if (onBack) {
                    backCells++;
                    if (walls.contains(w)) backWalls++;
                    if (room.windowFloor().contains(fk)) window = true;
                }
                if (m.placement().equals("centre") && !walls.isEmpty()) return null;
            }
        // every block of the module inside the room and not yet used
        boolean[] ok = {true};
        bp.forEach((p, state, be) -> {
            if (!ok[0] || state.isAir()) return;
            long k = o.offset(p).asLong();
            if (!room.air().contains(k) || st.taken.contains(k)) ok[0] = false;
            else if (!st.level.getBlockState(BlockPos.of(k)).isAir() && p.getY() > 0) ok[0] = false;
        });
        if (!ok[0]) return null;
        String place = m.placement();
        boolean wallBacked = backCells > 0 && backWalls == backCells;
        boolean corner = false;
        if (place.equals("wall") || place.equals("window") || place.equals("corner")) {
            if (!wallBacked) return null;
            if (place.equals("window") && !window) return null;
            for (Direction side : new Direction[]{w.getClockWise(), w.getCounterClockWise()}) {
                boolean all = true;
                for (int x = 0; x < s.getX(); x++)
                    for (int z = 0; z < s.getZ(); z++) {
                        if (inside(s, x + side.getStepX(), z + side.getStepZ())) continue;
                        if (!room.wallsAt(o.offset(x, 0, z).asLong()).contains(side)) all = false;
                    }
                if (all) corner = true;
            }
            if (place.equals("corner") && !corner) return null;
        }
        double fx = o.getX() + s.getX() / 2.0, fz = o.getZ() + s.getZ() / 2.0;
        double score = m.weight() + (m.modded() ? 20 : 0);
        switch (slot.prefer()) {
            case "far_from_door" -> score += 3 * doorDistance(room, fx, fz);
            case "near_window" -> score += window ? 30 : 0;
            case "corner" -> score += corner ? 25 : 0;
            case "centre" -> score -= 3 * Math.hypot(fx - st.cx, fz - st.cz);
            default -> score += wallBacked ? 5 : 0;
        }
        score += 8 * Hash.unit(st.seed, o, salt * 4 + rot.ordinal());
        return new Candidate(m, rot, bp, o, score, footprint);
    }

    private static boolean inside(Vec3i s, int x, int z) {
        return x >= 0 && z >= 0 && x < s.getX() && z < s.getZ();
    }

    private static double doorDistance(Room room, double x, double z) {
        if (room.doors().isEmpty()) return 0;
        double best = Double.MAX_VALUE;
        for (BlockPos d : room.doors()) best = Math.min(best, Math.abs(d.getX() + 0.5 - x) + Math.abs(d.getZ() + 0.5 - z));
        return best;
    }

    /** After taking {@code footprint}, every free floor cell must still be reachable from the doors (or from each other). */
    private static boolean walkable(State st, LongArrayList footprint) {
        LongOpenHashSet blocked = new LongOpenHashSet(st.takenFloor);
        blocked.addAll(footprint);
        LongOpenHashSet free = new LongOpenHashSet();
        for (long k : st.floorSorted) if (!blocked.contains(k)) free.add(k);
        if (free.isEmpty()) return st.room.doors().isEmpty();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        LongOpenHashSet seen = new LongOpenHashSet();
        boolean anyDoor = false;
        for (long k : st.room.doorClear()) {
            if (free.contains(k) && seen.add(k)) {
                q.enqueue(k);
                anyDoor = true;
            }
        }
        if (!anyDoor) {
            long first = free.iterator().nextLong();
            seen.add(first);
            q.enqueue(first);
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!q.isEmpty()) {
            p.set(q.dequeueLong());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                long n = BlockPos.offset(p.asLong(), d);
                if (free.contains(n) && seen.add(n)) q.enqueue(n);
            }
        }
        return seen.size() == free.size();
    }

    private static void place(State st, Candidate c) {
        c.bp().forEach((p, state, be) -> {
            if (state.isAir()) return;
            BlockPos w = c.origin().offset(p);
            st.out.put(w.asLong(), state);
            st.taken.add(w.asLong());
        });
        for (long k : c.footprint()) {
            st.takenFloor.add(k);
            st.taken.add(k);
        }
        st.placed.add(c.module().id());
    }

    /** The largest free rectangle (2x2 up to 5x5) of floor cells away from walls and doors gets a carpet. */
    private static void rug(State st) {
        Room room = st.room;
        LongOpenHashSet ok = new LongOpenHashSet();
        for (long k : st.floorSorted) {
            if (st.freeFloor(k) && !room.doorClear().contains(k) && room.wallsAt(k).isEmpty()) ok.add(k);
        }
        int bestArea = 0;
        BlockPos bestO = null;
        int bw = 0, bd = 0;
        for (long k : st.floorSorted) {
            if (!ok.contains(k)) continue;
            BlockPos o = BlockPos.of(k);
            for (int w = 2; w <= 5; w++)
                for (int d = 2; d <= 5; d++) {
                    if (w * d <= bestArea) continue;
                    boolean all = true;
                    for (int x = 0; x < w && all; x++)
                        for (int z = 0; z < d && all; z++) if (!ok.contains(o.offset(x, 0, z).asLong())) all = false;
                    if (all) {
                        bestArea = w * d;
                        bestO = o;
                        bw = w;
                        bd = d;
                    }
                }
        }
        if (bestO == null) return;
        DyeColor rugColor = COLORS[(int) Math.floorMod(Hash.mix(st.seed ^ 0xC0FFEEL), (long) COLORS.length)];
        Block carpet = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(rugColor.getSerializedName() + "_carpet"));
        for (int x = 0; x < bw; x++)
            for (int z = 0; z < bd; z++) {
                long k = bestO.offset(x, 0, z).asLong();
                st.out.put(k, carpet.defaultBlockState());
                st.takenFloor.add(k);
            }
        st.placed.add("rug");
    }

    /** A lantern (on a chain in tall rooms) hanging from the ceiling above the room centre. */
    private static void light(State st) {
        Room room = st.room;
        long best = 0;
        double bestD = Double.MAX_VALUE;
        for (long k : st.floorSorted) {
            if (room.height(k) < 3) continue;
            double d = Math.hypot(BlockPos.getX(k) - st.cx, BlockPos.getZ(k) - st.cz);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        if (bestD == Double.MAX_VALUE) return;
        BlockPos f = BlockPos.of(best);
        int h = room.height(best);
        BlockPos lantern = f.above(2);
        if (st.taken.contains(lantern.asLong())) return;
        for (int y = 3; y < h; y++) {
            if (st.taken.contains(f.above(y).asLong())) return;
        }
        for (int y = 3; y < h; y++) st.out.put(f.above(y).asLong(), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        st.out.put(lantern.asLong(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        st.placed.add("light");
    }

    private static ChangeSet build(State st) {
        ChangeSet.Builder b = ChangeSet.builder();
        LongOpenHashSet done = new LongOpenHashSet();
        for (var e : st.out.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            if (done.contains(k)) continue;
            BlockPos p = BlockPos.of(k);
            BlockState s = e.getValue();
            Change c = new Change(p, st.level.getBlockState(p), s, null, true);
            Optional<BlockPos> partner = Groups.partner(p, s);
            if (partner.isPresent() && st.out.containsKey(partner.get().asLong())) {
                BlockPos q = partner.get();
                done.add(k);
                done.add(q.asLong());
                Change pc = new Change(q, st.level.getBlockState(q), st.out.get(q.asLong()), null, true);
                b.group(Groups.isPrimary(s) ? List.of(c, pc) : List.of(pc, c));
            } else {
                done.add(k);
                b.add(c);
            }
        }
        return b.build();
    }

    /** The most used wood family in the room's shell (walls, floor, ceiling); oak when there is none. */
    public static Family houseWood(BlockGetter level, Room room) {
        java.util.Map<Family, Integer> counts = new java.util.HashMap<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long k : room.air()) {
            BlockPos p = BlockPos.of(k);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (room.air().contains(n.asLong()) || !seen.add(n.asLong())) continue;
                var m = com.stilwerk.family.FamilyIndex.get().lookup(level.getBlockState(n).getBlock());
                if (m != null && m.family().wood()) counts.merge(m.family(), 1, Integer::sum);
            }
        }
        Family best = null;
        int n = 0;
        for (var e : counts.entrySet()) {
            if (e.getValue() > n || (e.getValue() == n && best != null && e.getKey().id().compareTo(best.id()) < 0)) {
                best = e.getKey();
                n = e.getValue();
            }
        }
        return best != null ? best : com.stilwerk.family.FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace("oak"));
    }

    /** Guesses the room type from what is already there and the floor size. */
    public static RoomType auto(BlockGetter level, Room room) {
        int containers = 0;
        boolean bed = false, forge = false;
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long k : room.air()) {
            BlockPos p = BlockPos.of(k);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (room.air().contains(n.asLong()) || !seen.add(n.asLong())) continue;
                Block b = level.getBlockState(n).getBlock();
                if (b instanceof BedBlock) bed = true;
                if (b instanceof AbstractFurnaceBlock || b instanceof AnvilBlock || b == Blocks.SMITHING_TABLE) forge = true;
                if (b instanceof ChestBlock || b instanceof BarrelBlock) containers++;
            }
        }
        String id;
        if (forge) id = "workshop";
        else if (bed) id = "bedroom";
        else if (containers >= 3) id = "storage";
        else if (room.floor().size() <= 20) id = "bedroom";
        else if (room.floor().size() <= 45) id = "living";
        else id = "dining";
        return ModuleLibrary.type(id);
    }
}
