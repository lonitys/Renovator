package com.stilwerk.blueprint;

import com.stilwerk.core.NatureFilter;
import com.stilwerk.core.ResolvedSelection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A copied or imported structure. Each cell is VOID (leave the world as it is), air, or a block state.
 * Cells are indexed (y * sizeZ + z) * sizeX + x.
 */
public final class Blueprint {
    public static final int VOID = -1;

    private final Vec3i size;
    private final List<BlockState> palette;
    private final int[] cells;
    private final Int2ObjectMap<CompoundTag> blockEntities;
    private final BlueprintMeta meta;
    private int nonAir = -1;

    public Blueprint(Vec3i size, List<BlockState> palette, int[] cells, Int2ObjectMap<CompoundTag> blockEntities, BlueprintMeta meta) {
        if (cells.length != size.getX() * size.getY() * size.getZ()) throw new IllegalArgumentException("cell count does not match size");
        this.size = size;
        this.palette = Collections.unmodifiableList(new ArrayList<>(palette));
        this.cells = cells;
        this.blockEntities = blockEntities;
        this.meta = meta;
    }

    public Vec3i size() {
        return size;
    }

    public int volume() {
        return cells.length;
    }

    public int index(int x, int y, int z) {
        return (y * size.getZ() + z) * size.getX() + x;
    }

    public List<BlockState> palette() {
        return palette;
    }

    /** Raw palette index of a cell (VOID = -1). */
    public int cell(int index) {
        return cells[index];
    }

    public @Nullable BlockState state(int x, int y, int z) {
        int c = cells[index(x, y, z)];
        return c == VOID ? null : palette.get(c);
    }

    public @Nullable CompoundTag blockEntity(int x, int y, int z) {
        return blockEntities.get(index(x, y, z));
    }

    public Int2ObjectMap<CompoundTag> blockEntities() {
        return blockEntities;
    }

    public BlueprintMeta meta() {
        return meta;
    }

    public Blueprint withMeta(BlueprintMeta m) {
        return new Blueprint(size, palette, cells, blockEntities, m);
    }

    /** Number of non-VOID, non-air cells. */
    public int nonAir() {
        if (nonAir < 0) {
            int n = 0;
            for (int c : cells) if (c != VOID && !palette.get(c).isAir()) n++;
            nonAir = n;
        }
        return nonAir;
    }

    /** All non-VOID states, one per cell (for censuses). */
    public List<BlockState> states() {
        List<BlockState> out = new ArrayList<>(cells.length);
        for (int c : cells) if (c != VOID) out.add(palette.get(c));
        return out;
    }

    /** Visits every non-VOID cell with its position inside the blueprint. */
    public void forEach(CellVisitor v) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < size.getY(); y++)
            for (int z = 0; z < size.getZ(); z++)
                for (int x = 0; x < size.getX(); x++, i++) {
                    int c = cells[i];
                    if (c != VOID) v.visit(m.set(x, y, z), palette.get(c), blockEntities.get(i));
                }
    }

    @FunctionalInterface
    public interface CellVisitor {
        void visit(BlockPos.MutableBlockPos pos, BlockState state, @Nullable CompoundTag blockEntity);
    }

    /** A copy with every non-VOID state replaced by {@code f(pos, state)}. */
    public Blueprint mapStates(BiFunction<BlockPos, BlockState, BlockState> f) {
        Builder b = new Builder(size, meta);
        forEach((p, s, be) -> b.set(p.getX(), p.getY(), p.getZ(), f.apply(p, s), be));
        return b.build();
    }

    /** Copies the selection from the world. Box: air is stored as air; flood: non-members are VOID. */
    public static Blueprint capture(BlockGetter level, ResolvedSelection sel, boolean skipNature,
                                    @Nullable HolderLookup.Provider regs, BlueprintMeta meta) {
        BoundingBox bb = sel.bounds();
        Vec3i size = new Vec3i(bb.getXSpan(), bb.getYSpan(), bb.getZSpan());
        Builder b = new Builder(size, meta);
        NatureFilter nf = new NatureFilter(level);
        sel.forEach(p -> {
            BlockState s = level.getBlockState(p);
            if (skipNature && nf.isNatural(p, s)) return;
            CompoundTag tag = null;
            if (s.hasBlockEntity() && regs != null) {
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) tag = be.saveWithoutMetadata(regs);
            }
            b.set(p.getX() - bb.minX(), p.getY() - bb.minY(), p.getZ() - bb.minZ(), s, tag);
        });
        return b.build();
    }

    /** SHA-1 of the network form; identifies an uploaded blueprint. */
    public byte[] sha1() {
        try {
            return MessageDigest.getInstance("SHA-1").digest(BlueprintIO.toNetwork(this));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Incremental construction with an automatic palette. */
    public static final class Builder {
        private final Vec3i size;
        private final BlueprintMeta meta;
        private final List<BlockState> palette = new ArrayList<>();
        private final Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
        private final int[] cells;
        private final Int2ObjectMap<CompoundTag> bes = new Int2ObjectOpenHashMap<>();

        public Builder(Vec3i size, BlueprintMeta meta) {
            this.size = size;
            this.meta = meta;
            this.cells = new int[size.getX() * size.getY() * size.getZ()];
            Arrays.fill(cells, VOID);
            ids.defaultReturnValue(-1);
        }

        public Builder set(int x, int y, int z, BlockState s, @Nullable CompoundTag be) {
            int id = ids.getInt(s);
            if (id < 0) {
                id = palette.size();
                palette.add(s);
                ids.put(s, id);
            }
            int i = (y * size.getZ() + z) * size.getX() + x;
            cells[i] = id;
            if (be != null) bes.put(i, be);
            else bes.remove(i);
            return this;
        }

        public Builder air(int x, int y, int z) {
            return set(x, y, z, Blocks.AIR.defaultBlockState(), null);
        }

        public Blueprint build() {
            return new Blueprint(size, palette, cells, bes, meta);
        }
    }
}
