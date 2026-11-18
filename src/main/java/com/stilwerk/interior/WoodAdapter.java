package com.stilwerk.interior;

import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.family.StateTransfer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Turns oak furniture into the house's wood: vanilla by family and shape, modded blocks by their oak_ name. */
public final class WoodAdapter {
    private WoodAdapter() {}

    public static BlockState adapt(BlockState s, Family wood) {
        if (wood == null || wood.id().getPath().equals("oak")) return s;
        Block b = s.getBlock();
        FamilyIndex.Member m = FamilyIndex.get().lookup(b);
        if (m != null && m.family().id().equals(ResourceLocation.withDefaultNamespace("oak"))) {
            Block to = wood.get(m.shape());
            return to == null ? s : StateTransfer.transfer(s, to);
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        String path = id.getPath();
        int i = indexOfOak(path);
        if (i < 0) return s;
        String renamed = path.substring(0, i) + wood.id().getPath() + path.substring(i + 3);
        ResourceLocation target = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), renamed);
        if (!BuiltInRegistries.BLOCK.containsKey(target)) return s;
        return StateTransfer.transfer(s, BuiltInRegistries.BLOCK.get(target));
    }

    /** Index of the word "oak" that is not part of "dark_oak", or -1. */
    static int indexOfOak(String path) {
        int from = 0;
        while (true) {
            int i = path.indexOf("oak", from);
            if (i < 0) return -1;
            boolean wordStart = i == 0 || path.charAt(i - 1) == '_';
            boolean wordEnd = i + 3 == path.length() || path.charAt(i + 3) == '_';
            boolean dark = i >= 5 && path.startsWith("dark_", i - 5);
            if (wordStart && wordEnd && !dark) return i;
            from = i + 3;
        }
    }
}
