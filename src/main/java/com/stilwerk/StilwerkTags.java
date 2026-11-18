package com.stilwerk;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class StilwerkTags {
    private StilwerkTags() {}

    /** Terrain and plants: never swapped unless the player includes nature (creative only). */
    public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK, Stilwerk.id("natural"));
    /** Extra blocks that Stilwerk never changes. */
    public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK, Stilwerk.id("protected"));
}
