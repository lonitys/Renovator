package com.stilwerk.blueprint.importer;

import com.mojang.serialization.Dynamic;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Parses block states from schematic files and lifts old ones to the running version via the DataFixer. */
public final class BlockStateUpgrader {
    private BlockStateUpgrader() {}

    public static int currentVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /** {Name, Properties} of an older data version → current version. */
    public static CompoundTag upgrade(CompoundTag stateTag, int fromVersion) {
        int to = currentVersion();
        if (fromVersion >= to) return stateTag;
        return (CompoundTag) DataFixers.getDataFixer()
                .update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, stateTag), fromVersion, to)
                .getValue();
    }

    /** "ns:id[k=v,...]" → {Name, Properties}. */
    public static CompoundTag parseStateString(String s) {
        String name = s;
        Map<String, String> props = new TreeMap<>();
        int b = s.indexOf('[');
        if (b >= 0) {
            name = s.substring(0, b);
            String inner = s.substring(b + 1, s.endsWith("]") ? s.length() - 1 : s.length());
            for (String kv : inner.split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name.trim());
        if (!props.isEmpty()) {
            CompoundTag p = new CompoundTag();
            props.forEach(p::putString);
            tag.put("Properties", p);
        }
        return tag;
    }

    /** Resolves an (already upgraded) state tag; unknown blocks become air and are counted in {@code unknown}. */
    public static BlockState resolve(CompoundTag tag, HolderGetter<Block> blocks, Map<String, Integer> unknown) {
        String name = tag.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            unknown.merge(name, 1, Integer::sum);
            return Blocks.AIR.defaultBlockState();
        }
        return NbtUtils.readBlockState(blocks, tag);
    }

    /** "unknown: a ×3, b ×1" style warnings. */
    public static java.util.List<String> warnings(Map<String, Integer> unknown) {
        java.util.List<String> out = new java.util.ArrayList<>();
        new LinkedHashMap<>(unknown).forEach((k, v) -> out.add("unknown: " + k + " ×" + v));
        return out;
    }
}
