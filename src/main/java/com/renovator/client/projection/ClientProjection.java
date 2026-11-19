package com.renovator.client.projection;

import com.renovator.client.GhostMesh;
import com.renovator.core.Costs;
import com.renovator.projection.CellState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** A projection as the client sees it: its cells, their state in the client world, and one ghost mesh pair per layer. */
public final class ClientProjection {
    public final UUID id;
    public final String labelKey, labelArg;
    public final ResourceLocation dimension;
    final long[] pos;
    final BlockState[] target, old;
    final int[] group;
    /** {@link CellState} ordinal per cell, -1 before the first scan. */
    final byte[] state;
    /** Distinct y values, ascending. */
    final int[] layers;
    final Int2ObjectOpenHashMap<IntArrayList> byLayer = new Int2ObjectOpenHashMap<>();
    final Int2ObjectOpenHashMap<IntArrayList> byGroup = new Int2ObjectOpenHashMap<>();
    final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
    final Int2ObjectOpenHashMap<LayerMesh> meshes = new Int2ObjectOpenHashMap<>();
    final BlockPos anchor;
    int done, open, blocked;
    int scanCursor;
    boolean removeRequested;

    /** Ghosts of one layer: still to build (tinted by style) and blocked (red); rebuilt when a cell changes. */
    static final class LayerMesh {
        GhostMesh open, blocked;
        boolean dirty = true;

        void close() {
            if (open != null) open.close();
            if (blocked != null) blocked.close();
            open = blocked = null;
        }
    }

    ClientProjection(UUID id, String labelKey, String labelArg, ResourceLocation dimension, long[] pos, BlockState[] target, BlockState[] old, int[] group) {
        this.id = id;
        this.labelKey = labelKey;
        this.labelArg = labelArg;
        this.dimension = dimension;
        this.pos = pos;
        this.target = target;
        this.old = old;
        this.group = group;
        this.state = new byte[pos.length];
        java.util.Arrays.fill(state, (byte) -1);
        index.defaultReturnValue(-1);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        java.util.TreeSet<Integer> ys = new java.util.TreeSet<>();
        for (int i = 0; i < pos.length; i++) {
            int x = BlockPos.getX(pos[i]), y = BlockPos.getY(pos[i]), z = BlockPos.getZ(pos[i]);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            ys.add(y);
            index.put(pos[i], i);
            byLayer.computeIfAbsent(y, k -> new IntArrayList()).add(i);
            byGroup.computeIfAbsent(group[i], k -> new IntArrayList()).add(i);
        }
        this.anchor = pos.length == 0 ? BlockPos.ZERO : new BlockPos(minX, minY, minZ);
        this.layers = ys.stream().mapToInt(Integer::intValue).toArray();
        this.open = pos.length;
    }

    public Component label() {
        return com.renovator.projection.ProjectionOps.label(labelKey, labelArg);
    }

    public int size() {
        return pos.length;
    }

    public int done() {
        return done;
    }

    public boolean finished() {
        return pos.length > 0 && done == pos.length;
    }

    BlockPos pos(int i) {
        return BlockPos.of(pos[i]);
    }

    int y(int i) {
        return BlockPos.getY(pos[i]);
    }

    CellState cellState(int i) {
        return state[i] < 0 ? CellState.OPEN : CellState.values()[state[i]];
    }

    /** Re-checks up to {@code budget} cells against the world; true when something changed. */
    boolean scan(BlockGetter level, int budget) {
        boolean changed = false;
        int n = pos.length;
        for (int k = 0; k < Math.min(budget, n); k++) {
            int i = scanCursor;
            scanCursor = (scanCursor + 1) % n;
            byte s = (byte) CellState.of(level.getBlockState(BlockPos.of(pos[i])), target[i], old[i]).ordinal();
            if (s == state[i]) continue;
            count(state[i], -1);
            count(s, 1);
            state[i] = s;
            LayerMesh m = meshes.get(y(i));
            if (m != null) m.dirty = true;
            changed = true;
        }
        return changed;
    }

    private void count(byte s, int d) {
        if (s == CellState.DONE.ordinal()) done += d;
        else if (s == CellState.BLOCKED.ordinal()) blocked += d;
        else open += d;
    }

    /** Cells of a layer that are not built yet. */
    int openIn(int y) {
        IntArrayList cells = byLayer.get(y);
        if (cells == null) return 0;
        int n = 0;
        for (int i = 0; i < cells.size(); i++) if (cellState(cells.getInt(i)) != CellState.DONE) n++;
        return n;
    }

    /** The lowest layer with something left to build, or the top layer. */
    int firstOpenLayer() {
        for (int y : layers) if (openIn(y) > 0) return y;
        return layers.length == 0 ? 0 : layers[layers.length - 1];
    }

    /** Items the cells of one layer (or all) still need, from their group's cost. */
    Object2IntOpenHashMap<Item> need(Integer layer) {
        List<BlockState> states = new ArrayList<>();
        Set<Integer> groups = new HashSet<>();
        for (int i = 0; i < pos.length; i++) {
            if (layer != null && y(i) != layer) continue;
            if (cellState(i) != CellState.OPEN) continue;
            if (!groups.add(group[i])) continue;
            IntArrayList g = byGroup.get(group[i]);
            for (int j = 0; j < g.size(); j++) states.add(target[g.getInt(j)]);
        }
        return Costs.tally(states);
    }

    /** The items one cell's group costs (a door: one door item). */
    Set<Item> groupItems(int cell) {
        List<BlockState> states = new ArrayList<>();
        IntArrayList g = byGroup.get(group[cell]);
        for (int j = 0; j < g.size(); j++) states.add(target[g.getInt(j)]);
        return Costs.tally(states).keySet();
    }

    void closeMeshes() {
        for (LayerMesh m : meshes.values()) m.close();
        meshes.clear();
    }
}
