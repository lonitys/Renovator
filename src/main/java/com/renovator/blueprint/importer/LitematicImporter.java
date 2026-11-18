package com.renovator.blueprint.importer;

import com.renovator.blueprint.Blueprint;
import com.renovator.blueprint.BlueprintMeta;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Litematica schematics. All regions are merged into one blueprint; cells outside every region are VOID. */
public final class LitematicImporter {
    private LitematicImporter() {}

    private record Region(CompoundTag tag, int minX, int minY, int minZ, int sx, int sy, int sz) {}

    public static ImportResult read(CompoundTag root, HolderGetter<Block> blocks, String fallbackName) throws IOException {
        int dataVersion = root.contains("MinecraftDataVersion") ? root.getInt("MinecraftDataVersion") : 1631;
        CompoundTag regionsTag = root.getCompound("Regions");
        if (regionsTag.isEmpty()) throw new IOException("litematic without regions");
        List<Region> regions = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : regionsTag.getAllKeys().stream().sorted().toList()) {
            CompoundTag r = regionsTag.getCompound(key);
            CompoundTag pos = r.getCompound("Position"), size = r.getCompound("Size");
            int px = pos.getInt("x"), py = pos.getInt("y"), pz = pos.getInt("z");
            int sx = size.getInt("x"), sy = size.getInt("y"), sz = size.getInt("z");
            if (sx == 0 || sy == 0 || sz == 0) continue;
            int rx = px + (sx < 0 ? sx + 1 : 0), ry = py + (sy < 0 ? sy + 1 : 0), rz = pz + (sz < 0 ? sz + 1 : 0);
            Region reg = new Region(r, rx, ry, rz, Math.abs(sx), Math.abs(sy), Math.abs(sz));
            regions.add(reg);
            minX = Math.min(minX, rx); minY = Math.min(minY, ry); minZ = Math.min(minZ, rz);
            maxX = Math.max(maxX, rx + reg.sx() - 1); maxY = Math.max(maxY, ry + reg.sy() - 1); maxZ = Math.max(maxZ, rz + reg.sz() - 1);
        }
        if (regions.isEmpty()) throw new IOException("litematic with empty regions");
        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        if ((long) size.getX() * size.getY() * size.getZ() > 64_000_000L) throw new IOException("litematic too large: " + size);

        String name = root.getCompound("Metadata").getString("Name");
        Blueprint.Builder b = new Blueprint.Builder(size, new BlueprintMeta(name.isEmpty() ? fallbackName : name,
                root.getCompound("Metadata").getString("Author"), 0L, "litematic"));
        Map<String, Integer> unknown = new HashMap<>();
        for (Region r : regions) {
            ListTag paletteTag = r.tag().getList("BlockStatePalette", Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = BlockStateUpgrader.resolve(BlockStateUpgrader.upgrade(paletteTag.getCompound(i), dataVersion), blocks, unknown);
            }
            long[] arr = r.tag().getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length - 1)));
            long volume = (long) r.sx() * r.sy() * r.sz();
            if ((volume * bits + 63) / 64 > arr.length) throw new IOException("litematic block array too short");
            int ox = r.minX() - minX, oy = r.minY() - minY, oz = r.minZ() - minZ;
            for (int i = 0; i < volume; i++) {
                int v = value(arr, i, bits);
                if (v >= palette.length) continue;
                BlockState st = palette[v];
                if (st.is(Blocks.STRUCTURE_VOID)) continue;
                int x = i % r.sx(), z = (i / r.sx()) % r.sz(), y = i / (r.sx() * r.sz());
                b.set(ox + x, oy + y, oz + z, st, null);
            }
            ListTag tiles = r.tag().getList("TileEntities", Tag.TAG_COMPOUND);
            Blueprint partial = b.build();
            for (int i = 0; i < tiles.size(); i++) {
                CompoundTag t = tiles.getCompound(i).copy();
                int x = ox + t.getInt("x"), y = oy + t.getInt("y"), z = oz + t.getInt("z");
                if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ()) continue;
                BlockState st = partial.state(x, y, z);
                if (st == null || !st.hasBlockEntity()) continue;
                t.remove("x");
                t.remove("y");
                t.remove("z");
                b.set(x, y, z, st, t);
            }
        }
        return new ImportResult(b.build(), BlockStateUpgrader.warnings(unknown));
    }

    /** Value i of a tightly packed array whose entries may span two longs (LitematicaBitArray). */
    static int value(long[] arr, int i, int bits) {
        long mask = (1L << bits) - 1;
        long start = (long) i * bits;
        int a = (int) (start >> 6), e = (int) (((long) (i + 1) * bits - 1) >> 6);
        int off = (int) (start & 63);
        if (a == e) return (int) ((arr[a] >>> off) & mask);
        return (int) (((arr[a] >>> off) | (arr[e] << (64 - off))) & mask);
    }
}
