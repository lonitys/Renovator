package com.renovator.blueprint;

import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.core.Groups;
import com.renovator.core.NatureFilter;
import com.renovator.core.Protection;
import com.renovator.family.StateTransfer;
import com.renovator.mix.MixPalette;
import com.renovator.swap.SwapMapping;
import com.renovator.swap.SwapPlanner;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Plans placing an (already rotated/mirrored) blueprint at an origin, optionally restyled by a swap mapping. */
public final class BlueprintPlanner {
    private BlueprintPlanner() {}

    public record Options(boolean overwrite, boolean survival) {}

    public record Result(ChangeSet changes, int blocked, List<SwapPlanner.Kept> kept) {}

    /** The blueprint restyled by the mapping (positions are evaluated at origin + cell for mixes). */
    public record Styled(Blueprint blueprint, IntOpenHashSet reshaped, List<SwapPlanner.Kept> kept) {}

    public static Styled style(Blueprint bp, BlockPos origin, SwapMapping mapping, Map<String, MixPalette> mixes, long seed) {
        if (mapping.isEmpty()) return new Styled(bp, new IntOpenHashSet(), List.of());
        SwapPlanner.Mapper mapper = new SwapPlanner.Mapper(mapping, mixes, seed);
        if (mapper.usesMixes()) {
            bp.forEach((p, s, be) -> {
                if (styleable(s)) mapper.note(origin.offset(p), s);
            });
            mapper.prepare();
        }
        Vec3i size = bp.size();
        Blueprint.Builder b = new Blueprint.Builder(size, bp.meta());
        bp.forEach((p, s, be) -> b.set(p.getX(), p.getY(), p.getZ(), s, be));
        IntOpenHashSet reshaped = new IntOpenHashSet();
        bp.forEach((p, s, be) -> {
            if (!styleable(s) || !Groups.isPrimary(s)) return;
            SwapPlanner.Mapped m = mapper.map(s, origin.offset(p));
            if (m == null) return;
            b.set(p.getX(), p.getY(), p.getZ(), m.state(), be);
            if (m.reshape()) reshaped.add(bp.index(p.getX(), p.getY(), p.getZ()));
            Optional<BlockPos> partner = Groups.partner(p.immutable(), s);
            if (partner.isPresent() && inside(size, partner.get())) {
                BlockPos q = partner.get();
                BlockState ps = bp.state(q.getX(), q.getY(), q.getZ());
                if (ps != null && ps.getBlock() == s.getBlock()) {
                    b.set(q.getX(), q.getY(), q.getZ(), StateTransfer.transfer(ps, m.state().getBlock()), bp.blockEntity(q.getX(), q.getY(), q.getZ()));
                }
            }
        });
        return new Styled(b.build(), reshaped, mapper.kept());
    }

    private static boolean styleable(BlockState s) {
        if (s.isAir() || NatureFilter.isNaturalState(s)) return false;
        return !s.hasBlockEntity() || s.getBlock() instanceof SignBlock;
    }

    static boolean inside(Vec3i size, BlockPos p) {
        return p.getX() >= 0 && p.getY() >= 0 && p.getZ() >= 0 && p.getX() < size.getX() && p.getY() < size.getY() && p.getZ() < size.getZ();
    }

    public static Result plan(BlockGetter level, Blueprint transformed, BlockPos origin, SwapMapping mapping,
                              Map<String, MixPalette> mixes, long seed, Options opt) {
        Styled styled = style(transformed, origin, mapping, mixes, seed);
        Blueprint bp = styled.blueprint();
        Vec3i size = bp.size();
        ChangeSet.Builder out = ChangeSet.builder();
        int[] blocked = {0};
        bp.forEach((p, s, be) -> {
            if (s.is(Blocks.STRUCTURE_VOID)) return;
            if (!Groups.isPrimary(s)) {
                Optional<BlockPos> owner = Groups.partner(p.immutable(), s);
                if (owner.isEmpty() || !inside(size, owner.get())) return; // lone secondary half: never placed
                return; // placed together with its primary half
            }
            boolean reshape = styled.reshaped().contains(bp.index(p.getX(), p.getY(), p.getZ()));
            Change c = change(level, origin.offset(p), s, be, reshape, opt);
            if (c == null) {
                if (blockedAt(level, origin.offset(p), s, opt)) blocked[0]++;
                return;
            }
            Optional<BlockPos> partner = Groups.partner(p.immutable(), s);
            if (partner.isPresent() && inside(size, partner.get())) {
                BlockPos q = partner.get();
                BlockState ps = bp.state(q.getX(), q.getY(), q.getZ());
                if (ps != null && ps.getBlock() == s.getBlock()) {
                    Change pc = change(level, origin.offset(q), ps, bp.blockEntity(q.getX(), q.getY(), q.getZ()), reshape, opt);
                    if (pc == null) {
                        if (blockedAt(level, origin.offset(q), ps, opt)) blocked[0]++;
                        return;
                    }
                    List<Change> g = new ArrayList<>(2);
                    g.add(c);
                    g.add(pc);
                    out.group(g);
                    return;
                }
            }
            out.add(c);
        });
        return new Result(out.build(), blocked[0], styled.kept());
    }

    private static @Nullable Change change(BlockGetter level, BlockPos target, BlockState s, @Nullable CompoundTag be, boolean reshape, Options opt) {
        BlockState current = level.getBlockState(target);
        if (current == s) return null;
        if (s.isAir()) {
            if (!opt.overwrite() || opt.survival() || current.isAir() || Protection.isProtected(level, target, current)) return null;
            return new Change(target, current, s, null, false);
        }
        if (blockedAt(level, target, s, opt)) return null;
        CompoundTag tag = be;
        if (tag != null && opt.survival() && !(s.getBlock() instanceof SignBlock)) tag = null;
        return new Change(target, current, s, tag == null ? null : tag.copy(), reshape);
    }

    /** True when an existing block keeps a non-air blueprint block from being placed. */
    private static boolean blockedAt(BlockGetter level, BlockPos target, BlockState s, Options opt) {
        if (s.isAir()) return false;
        BlockState current = level.getBlockState(target);
        if (current == s || current.isAir()) return false;
        if (Protection.isProtected(level, target, current)) return true;
        if (opt.survival() || !opt.overwrite()) return !current.canBeReplaced();
        return false;
    }
}
