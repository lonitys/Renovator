package com.stilwerk.blueprint;

import com.stilwerk.blueprint.importer.BlockStateUpgrader;
import com.stilwerk.blueprint.importer.VanillaNbtImporter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.VarInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Our file format (a vanilla structure .nbt plus a "stilwerk" compound) and the compact network form. */
public final class BlueprintIO {
    private BlueprintIO() {}

    private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;

    public static CompoundTag toStructureTag(Blueprint bp) {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", BlockStateUpgrader.currentVersion());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(bp.size().getX()));
        size.add(IntTag.valueOf(bp.size().getY()));
        size.add(IntTag.valueOf(bp.size().getZ()));
        root.put("size", size);
        ListTag palette = new ListTag();
        for (BlockState s : bp.palette()) palette.add(NbtUtils.writeBlockState(s));
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        int[] i = {0};
        Vec3i s = bp.size();
        for (int y = 0; y < s.getY(); y++)
            for (int z = 0; z < s.getZ(); z++)
                for (int x = 0; x < s.getX(); x++) {
                    int idx = bp.index(x, y, z);
                    int c = bp.cell(idx);
                    if (c == Blueprint.VOID) continue;
                    CompoundTag e = new CompoundTag();
                    ListTag pos = new ListTag();
                    pos.add(IntTag.valueOf(x));
                    pos.add(IntTag.valueOf(y));
                    pos.add(IntTag.valueOf(z));
                    e.put("pos", pos);
                    e.putInt("state", c);
                    CompoundTag be = bp.blockEntities().get(idx);
                    if (be != null) e.put("nbt", be.copy());
                    blocks.add(e);
                    i[0]++;
                }
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        root.put("stilwerk", bp.meta().save());
        return root;
    }

    public static void write(Blueprint bp, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        NbtIo.writeCompressed(toStructureTag(bp), file);
    }

    public static Blueprint read(Path file, HolderGetter<Block> blocks) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_NBT_BYTES));
        String name = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return VanillaNbtImporter.read(root, blocks, name).blueprint();
    }

    // ---------------------------------------------------------------- network form

    /** gzip(NBT{size, palette, cells (varints of index+1, VOID = 0), be list, meta}). Deterministic. */
    public static byte[] toNetwork(Blueprint bp) {
        CompoundTag root = new CompoundTag();
        root.putIntArray("size", new int[]{bp.size().getX(), bp.size().getY(), bp.size().getZ()});
        ListTag palette = new ListTag();
        for (BlockState s : bp.palette()) palette.add(NbtUtils.writeBlockState(s));
        root.put("palette", palette);
        ByteBuf buf = Unpooled.buffer(bp.volume());
        for (int i = 0; i < bp.volume(); i++) VarInt.write(buf, bp.cell(i) + 1);
        byte[] cells = new byte[buf.readableBytes()];
        buf.readBytes(cells);
        root.putByteArray("cells", cells);
        ListTag bes = new ListTag();
        bp.blockEntities().int2ObjectEntrySet().stream()
                .sorted(java.util.Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey))
                .forEach(e -> {
                    CompoundTag t = new CompoundTag();
                    t.putInt("i", e.getIntKey());
                    t.put("tag", e.getValue());
                    bes.add(t);
                });
        root.put("be", bes);
        root.put("meta", bp.meta().save());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Blueprint fromNetwork(byte[] data, HolderGetter<Block> blocks, long maxVolume) throws IOException {
        CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MAX_NBT_BYTES));
        int[] size = root.getIntArray("size");
        if (size.length != 3 || size[0] <= 0 || size[1] <= 0 || size[2] <= 0) throw new IOException("bad size");
        long volume = (long) size[0] * size[1] * size[2];
        if (volume > maxVolume) throw new IOException("blueprint too large: " + volume);
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        HashMap<String, Integer> unknown = new HashMap<>();
        for (int i = 0; i < paletteTag.size(); i++) palette.add(BlockStateUpgrader.resolve(paletteTag.getCompound(i), blocks, unknown));
        ByteBuf buf = Unpooled.wrappedBuffer(root.getByteArray("cells"));
        int[] cells = new int[(int) volume];
        for (int i = 0; i < cells.length; i++) {
            int v = VarInt.read(buf) - 1;
            if (v < Blueprint.VOID || v >= palette.size()) throw new IOException("bad cell " + v);
            cells[i] = v;
        }
        Int2ObjectMap<CompoundTag> bes = new Int2ObjectOpenHashMap<>();
        ListTag beList = root.getList("be", Tag.TAG_COMPOUND);
        for (int i = 0; i < beList.size(); i++) {
            CompoundTag t = beList.getCompound(i);
            int idx = t.getInt("i");
            if (idx >= 0 && idx < cells.length) bes.put(idx, t.getCompound("tag"));
        }
        return new Blueprint(new Vec3i(size[0], size[1], size[2]), palette, cells, bes, BlueprintMeta.load(root.getCompound("meta")));
    }
}
