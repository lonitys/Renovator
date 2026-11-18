package com.stilwerk.blueprint.importer;

import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintMeta;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla structure files (structure blocks, jigsaw pieces, and our own blueprints). Missing positions are VOID. */
public final class VanillaNbtImporter {
    private VanillaNbtImporter() {}

    public static ImportResult read(CompoundTag root, HolderGetter<Block> blocks, String fallbackName) throws IOException {
        int version = NbtUtils.getDataVersion(root, 500);
        if (version < BlockStateUpgrader.currentVersion()) {
            root = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), root, version);
        }
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() != 3) throw new IOException("structure without size");
        Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0 || (long) size.getX() * size.getY() * size.getZ() > 64_000_000L) {
            throw new IOException("invalid structure size " + size);
        }
        ListTag paletteTag = root.contains("palette", Tag.TAG_LIST) ? root.getList("palette", Tag.TAG_COMPOUND)
                : root.getList("palettes", Tag.TAG_LIST).getList(0);
        Map<String, Integer> unknown = new HashMap<>();
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = BlockStateUpgrader.resolve(paletteTag.getCompound(i), blocks, unknown);

        BlueprintMeta meta = root.contains("stilwerk", Tag.TAG_COMPOUND)
                ? BlueprintMeta.load(root.getCompound("stilwerk"))
                : new BlueprintMeta(fallbackName, "", 0L, "nbt");
        if (meta.name().isEmpty()) meta = meta.withName(fallbackName);
        Blueprint.Builder b = new Blueprint.Builder(size, meta);
        ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ListTag pos = e.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            int st = e.getInt("state");
            if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ() || st < 0 || st >= palette.length) continue;
            BlockState s = palette[st];
            if (s.is(Blocks.STRUCTURE_VOID)) continue;
            b.set(x, y, z, s, e.contains("nbt", Tag.TAG_COMPOUND) ? e.getCompound("nbt") : null);
        }
        return new ImportResult(b.build(), BlockStateUpgrader.warnings(unknown));
    }
}
