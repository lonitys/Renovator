package com.stilwerk.blueprint.importer;

import com.mojang.serialization.Dynamic;
import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintMeta;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Sponge schematics v2 and v3 (WorldEdit, FAWE). Index order x + z*W + y*W*L. */
public final class SpongeSchemImporter {
    private SpongeSchemImporter() {}

    public static ImportResult read(CompoundTag root, HolderGetter<Block> blocks, String name) throws IOException {
        CompoundTag s = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        int version = s.getInt("Version");
        if (version < 1 || version > 3) throw new IOException("unsupported schematic version " + version);
        int dataVersion = s.contains("DataVersion") ? s.getInt("DataVersion") : 1519;
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, l = s.getShort("Length") & 0xFFFF;
        if (w == 0 || h == 0 || l == 0) throw new IOException("empty schematic");
        CompoundTag palette;
        byte[] data;
        ListTag bes;
        if (version >= 3) {
            CompoundTag b = s.getCompound("Blocks");
            palette = b.getCompound("Palette");
            data = b.getByteArray("Data");
            bes = b.getList("BlockEntities", Tag.TAG_COMPOUND);
        } else {
            palette = s.getCompound("Palette");
            data = s.getByteArray("BlockData");
            bes = s.getList(version == 1 ? "TileEntities" : "BlockEntities", Tag.TAG_COMPOUND);
        }
        if (palette.isEmpty()) throw new IOException("schematic without palette");

        Map<String, Integer> unknown = new HashMap<>();
        int max = 0;
        for (String key : palette.getAllKeys()) max = Math.max(max, palette.getInt(key));
        BlockState[] states = new BlockState[max + 1];
        for (String key : palette.getAllKeys()) {
            CompoundTag tag = BlockStateUpgrader.upgrade(BlockStateUpgrader.parseStateString(key), dataVersion);
            states[palette.getInt(key)] = BlockStateUpgrader.resolve(tag, blocks, unknown);
        }

        Blueprint.Builder b = new Blueprint.Builder(new Vec3i(w, h, l), new BlueprintMeta(name, "", 0L, "schem"));
        int i = 0, index = 0, volume = w * h * l;
        while (i < data.length && index < volume) {
            int value = 0, shift = 0, by;
            do {
                if (i >= data.length) throw new IOException("truncated block data");
                by = data[i++] & 0xFF;
                value |= (by & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("varint too long");
            } while ((by & 0x80) != 0);
            int x = index % w, z = (index / w) % l, y = index / (w * l);
            index++;
            BlockState st = value < states.length ? states[value] : null;
            if (st == null || st.is(Blocks.STRUCTURE_VOID)) continue;
            b.set(x, y, z, st, null);
        }
        Blueprint partial = b.build();

        // block entities: v3 {Pos, Id, Data{...}}, v1/v2 {Pos, Id, ...data}
        for (int k = 0; k < bes.size(); k++) {
            CompoundTag e = bes.getCompound(k);
            int[] pos = e.getIntArray("Pos");
            if (pos.length != 3 || pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= w || pos[1] >= h || pos[2] >= l) continue;
            BlockState st = partial.state(pos[0], pos[1], pos[2]);
            if (st == null || !st.hasBlockEntity()) continue;
            CompoundTag tag;
            if (version >= 3) {
                tag = e.getCompound("Data").copy();
            } else {
                tag = e.copy();
                tag.remove("Pos");
                tag.remove("Id");
            }
            tag.putString("id", e.getString("Id"));
            if (dataVersion < BlockStateUpgrader.currentVersion()) {
                tag = (CompoundTag) DataFixers.getDataFixer().update(References.BLOCK_ENTITY, new Dynamic<>(NbtOps.INSTANCE, tag),
                        dataVersion, BlockStateUpgrader.currentVersion()).getValue();
            }
            b.set(pos[0], pos[1], pos[2], st, tag);
        }
        return new ImportResult(b.build(), BlockStateUpgrader.warnings(unknown));
    }
}
