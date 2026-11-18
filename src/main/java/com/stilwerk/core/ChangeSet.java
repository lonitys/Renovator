package com.stilwerk.core;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Ordered groups of changes; a group (e.g. both door halves) is always applied as a whole. */
public final class ChangeSet {
    public static final ChangeSet EMPTY = new ChangeSet(List.of());

    private final List<List<Change>> groups;
    private final int size;

    private ChangeSet(List<List<Change>> groups) {
        this.groups = Collections.unmodifiableList(groups);
        int n = 0;
        for (List<Change> g : groups) n += g.size();
        this.size = n;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<List<Change>> groups = new ArrayList<>();

        public Builder add(Change c) {
            groups.add(List.of(c));
            return this;
        }

        public Builder group(List<Change> g) {
            if (!g.isEmpty()) groups.add(List.copyOf(g));
            return this;
        }

        public int size() {
            int n = 0;
            for (List<Change> g : groups) n += g.size();
            return n;
        }

        public ChangeSet build() {
            return new ChangeSet(new ArrayList<>(groups));
        }
    }

    public List<List<Change>> groups() {
        return groups;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Every change in order. */
    public List<Change> changes() {
        List<Change> out = new ArrayList<>(size);
        for (List<Change> g : groups) out.addAll(g);
        return out;
    }

    /** The undo of this set: each change reversed, groups in reverse order. */
    public ChangeSet inverse() {
        List<List<Change>> out = new ArrayList<>(groups.size());
        for (int i = groups.size() - 1; i >= 0; i--) {
            List<Change> g = groups.get(i);
            List<Change> r = new ArrayList<>(g.size());
            for (Change c : g) r.add(c.inverse());
            out.add(r);
        }
        return new ChangeSet(out);
    }

    public CompoundTag save() {
        Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
        ListTag palette = new ListTag();
        long[] pos = new long[size];
        int[] olds = new int[size], news = new int[size], groupSizes = new int[groups.size()];
        byte[] reshape = new byte[size];
        ListTag bes = new ListTag();
        int i = 0, gi = 0;
        for (List<Change> g : groups) {
            groupSizes[gi++] = g.size();
            for (Change c : g) {
                pos[i] = c.pos().asLong();
                olds[i] = id(ids, palette, c.oldState());
                news[i] = id(ids, palette, c.newState());
                reshape[i] = (byte) (c.reshape() ? 1 : 0);
                if (c.blockEntity() != null) {
                    CompoundTag be = new CompoundTag();
                    be.putInt("i", i);
                    be.put("tag", c.blockEntity().copy());
                    bes.add(be);
                }
                i++;
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("palette", palette);
        tag.putLongArray("pos", pos);
        tag.putIntArray("old", olds);
        tag.putIntArray("new", news);
        tag.putByteArray("reshape", reshape);
        tag.putIntArray("groups", groupSizes);
        tag.put("be", bes);
        return tag;
    }

    private static int id(Object2IntOpenHashMap<BlockState> ids, ListTag palette, BlockState s) {
        if (ids.containsKey(s)) return ids.getInt(s);
        int id = palette.size();
        palette.add(NbtUtils.writeBlockState(s));
        ids.put(s, id);
        return id;
    }

    public static ChangeSet load(CompoundTag tag, HolderGetter<Block> blocks) {
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompound(i));
        long[] pos = tag.getLongArray("pos");
        int[] olds = tag.getIntArray("old"), news = tag.getIntArray("new"), groupSizes = tag.getIntArray("groups");
        byte[] reshape = tag.getByteArray("reshape");
        CompoundTag[] bes = new CompoundTag[pos.length];
        ListTag beList = tag.getList("be", Tag.TAG_COMPOUND);
        for (int i = 0; i < beList.size(); i++) {
            CompoundTag be = beList.getCompound(i);
            bes[be.getInt("i")] = be.getCompound("tag");
        }
        List<List<Change>> groups = new ArrayList<>(groupSizes.length);
        int i = 0;
        for (int gs : groupSizes) {
            List<Change> g = new ArrayList<>(gs);
            for (int k = 0; k < gs; k++, i++) {
                g.add(new Change(BlockPos.of(pos[i]), palette[olds[i]], palette[news[i]], bes[i], reshape[i] != 0));
            }
            groups.add(g);
        }
        return new ChangeSet(groups);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChangeSet c && c.groups.equals(groups);
    }

    @Override
    public int hashCode() {
        return groups.hashCode();
    }
}
