package com.stilwerk.swap;

import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.Groups;
import com.stilwerk.core.NatureFilter;
import com.stilwerk.core.Protection;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.family.Shape;
import com.stilwerk.family.StateTransfer;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.mix.PickContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** Plans a style swap: (world, selection, mapping, mixes, seed) → ChangeSet. Pure and deterministic. */
public final class SwapPlanner {
    private SwapPlanner() {}

    public record Options(boolean includeNature, boolean survival) {}

    /** Blocks that stayed because the target family lacks their shape. */
    public record Kept(ResourceLocation source, Shape shape, Target target, int count) {}

    public record Result(ChangeSet changes, List<Kept> kept) {}

    public record Census(Family family, int count, Map<Shape, Integer> shapes) {}

    public record Mapped(BlockState state, boolean reshape) {}

    /**
     * The per-state mapping shared by the swap and blueprint planners. When the mapping uses mix palettes,
     * every candidate position must be {@link #note noted} first and {@link #prepare} called, so that edge
     * and gradient patterns know the extent of each source family.
     */
    public static final class Mapper {
        private final SwapMapping mapping;
        private final Map<String, MixPalette> mixes;
        private final long seed;
        private final FamilyIndex index = FamilyIndex.get();
        private final Map<ResourceLocation, LongOpenHashSet> notes = new HashMap<>();
        private final Map<ResourceLocation, PickContext> contexts = new HashMap<>();
        private final Map<List<Object>, int[]> kept = new LinkedHashMap<>();

        public Mapper(SwapMapping mapping, Map<String, MixPalette> mixes, long seed) {
            this.mapping = mapping;
            this.mixes = mixes;
            this.seed = seed;
        }

        public boolean usesMixes() {
            return !mapping.mixIds().isEmpty();
        }

        public void note(BlockPos pos, BlockState state) {
            FamilyIndex.Member m = index.lookup(state.getBlock());
            if (m == null) return;
            if (mapping.resolve(m.family().id(), m.shape()).kind() != Target.Kind.MIX) return;
            notes.computeIfAbsent(m.family().id(), k -> new LongOpenHashSet()).add(pos.asLong());
        }

        public void prepare() {
            notes.forEach((family, members) -> {
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (long l : members) {
                    int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
                    minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                }
                contexts.put(family, new PickContext(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), members));
            });
        }

        /** New state for a primary block, or null when it stays. */
        public @Nullable Mapped map(BlockState state, BlockPos pos) {
            FamilyIndex.Member m = index.lookup(state.getBlock());
            if (m == null) return null;
            Target t = mapping.resolve(m.family().id(), m.shape());
            Family target;
            switch (t.kind()) {
                case KEEP -> {
                    return null;
                }
                case FAMILY -> {
                    target = t.family().map(index::byId).orElse(null);
                    if (target == null) return null;
                }
                default -> {
                    MixPalette mix = t.mix().map(mixes::get).orElse(null);
                    if (mix == null) return null;
                    PickContext ctx = contexts.computeIfAbsent(m.family().id(),
                            k -> new PickContext(new BoundingBox(pos), new LongOpenHashSet(new long[]{pos.asLong()})));
                    target = index.byId(mix.pick(seed, pos, ctx));
                    if (target == null || target.resolve(m.shape()) == null) target = index.byId(mix.base());
                    if (target == null) return null;
                }
            }
            Shape shape = target.resolve(m.shape());
            if (shape == null) {
                kept.computeIfAbsent(List.of(m.family().id(), m.shape(), t), k -> new int[1])[0]++;
                return null;
            }
            BlockState out = StateTransfer.transfer(state, target.get(shape));
            if (out == state) return null;
            return new Mapped(out, shape != m.shape());
        }

        public List<Kept> kept() {
            List<Kept> out = new ArrayList<>();
            kept.forEach((k, n) -> out.add(new Kept((ResourceLocation) k.get(0), (Shape) k.get(1), (Target) k.get(2), n[0])));
            return out;
        }
    }

    public static Result plan(BlockGetter level, ResolvedSelection sel, SwapMapping mapping, Map<String, MixPalette> mixes,
                              long seed, Options opt) {
        boolean nature = opt.includeNature() && !opt.survival();
        NatureFilter nf = new NatureFilter(level);
        Mapper mapper = new Mapper(mapping, mixes, seed);
        if (mapper.usesMixes()) {
            sel.forEach(p -> {
                BlockState s = level.getBlockState(p);
                if (eligible(level, nf, p, s, nature)) mapper.note(p, s);
            });
            mapper.prepare();
        }
        HolderLookup.Provider regs = level instanceof LevelReader lr ? lr.registryAccess() : null;
        ChangeSet.Builder b = ChangeSet.builder();
        sel.forEach(p -> {
            BlockState s = level.getBlockState(p);
            if (!eligible(level, nf, p, s, nature) || !Groups.isPrimary(s)) return;
            Mapped m = mapper.map(s, p);
            if (m == null) return;
            Change c = new Change(p, s, m.state(), signData(level, p, s, m.state(), regs), m.reshape());
            Optional<BlockPos> partner = Groups.partner(p, s);
            if (partner.isPresent()) {
                BlockPos pp = partner.get();
                BlockState ps = level.getBlockState(pp);
                if (ps.getBlock() == s.getBlock()) {
                    if (Protection.isProtected(level, pp, ps)) return;
                    BlockState pn = StateTransfer.transfer(ps, m.state().getBlock());
                    b.group(List.of(c, new Change(pp, ps, pn, null, m.reshape())));
                    return;
                }
            }
            b.add(c);
        });
        return new Result(b.build(), mapper.kept());
    }

    static boolean eligible(BlockGetter level, NatureFilter nf, BlockPos p, BlockState s, boolean nature) {
        if (s.isAir()) return false;
        if (Protection.isProtected(level, p, s)) return false;
        return nature || !nf.isNatural(p, s);
    }

    /** Sign text travels with a sign that changes wood type. */
    static @Nullable CompoundTag signData(BlockGetter level, BlockPos p, BlockState from, BlockState to, @Nullable HolderLookup.Provider regs) {
        if (regs == null || !(from.getBlock() instanceof SignBlock) || !(to.getBlock() instanceof SignBlock)) return null;
        BlockEntity be = level.getBlockEntity(p);
        return be == null ? null : be.saveWithoutMetadata(regs);
    }

    /** Families present in the selection (built and unprotected unless nature is included), most frequent first. */
    public static List<Census> census(BlockGetter level, ResolvedSelection sel, boolean includeNature) {
        NatureFilter nf = new NatureFilter(level);
        Map<Family, int[]> counts = new HashMap<>();
        Map<Family, EnumMap<Shape, Integer>> shapes = new HashMap<>();
        sel.forEach(p -> {
            BlockState s = level.getBlockState(p);
            if (!eligible(level, nf, p, s, includeNature) || !Groups.isPrimary(s)) return;
            count(s, counts, shapes);
        });
        return sorted(counts, shapes);
    }

    /** Census over plain states (blueprints); nature is judged by tag and leaf persistence only. */
    public static List<Census> censusStates(Iterable<BlockState> states) {
        Map<Family, int[]> counts = new HashMap<>();
        Map<Family, EnumMap<Shape, Integer>> shapes = new HashMap<>();
        for (BlockState s : states) {
            if (s.isAir() || NatureFilter.isNaturalState(s) || !Groups.isPrimary(s)) continue;
            if (s.hasBlockEntity() && !(s.getBlock() instanceof SignBlock)) continue;
            count(s, counts, shapes);
        }
        return sorted(counts, shapes);
    }

    private static void count(BlockState s, Map<Family, int[]> counts, Map<Family, EnumMap<Shape, Integer>> shapes) {
        FamilyIndex.Member m = FamilyIndex.get().lookup(s.getBlock());
        if (m == null) return;
        counts.computeIfAbsent(m.family(), k -> new int[1])[0]++;
        shapes.computeIfAbsent(m.family(), k -> new EnumMap<>(Shape.class)).merge(m.shape(), 1, Integer::sum);
    }

    private static List<Census> sorted(Map<Family, int[]> counts, Map<Family, EnumMap<Shape, Integer>> shapes) {
        List<Census> out = new ArrayList<>();
        counts.forEach((f, n) -> out.add(new Census(f, n[0], shapes.get(f))));
        out.sort(Comparator.comparingInt(Census::count).reversed().thenComparing(c -> c.family().id()));
        return out;
    }
}
