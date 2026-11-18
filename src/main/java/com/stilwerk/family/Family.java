package com.stilwerk.family;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** A material with its shapes, e.g. {@code minecraft:oak} = planks, stairs, slab, fence, door, logs ... */
public record Family(ResourceLocation id, Map<Shape, Block> blocks, boolean wood) {
    public Family {
        blocks = Collections.unmodifiableMap(new EnumMap<>(blocks));
    }

    public @Nullable Block get(Shape s) {
        return blocks.get(s);
    }

    public boolean has(Shape s) {
        return blocks.containsKey(s);
    }

    /** FULL when present, else the first shape in enum order. */
    public Block icon() {
        Block full = blocks.get(Shape.FULL);
        if (full != null) return full;
        for (Shape s : Shape.values()) {
            Block b = blocks.get(s);
            if (b != null) return b;
        }
        throw new IllegalStateException("empty family " + id);
    }

    public boolean singleton() {
        return blocks.size() == 1 && blocks.containsKey(Shape.FULL);
    }

    /** First shape of {@code s.chain()} this family has, or null when the block has to stay. */
    public @Nullable Shape resolve(Shape s) {
        for (Shape c : s.chain()) if (blocks.containsKey(c)) return c;
        return null;
    }

    /** Translation key used for a friendly family name in the UI (falls back to the icon block's name). */
    public String translationKey() {
        return "stilwerk.family." + id.getNamespace() + "." + id.getPath();
    }
}
