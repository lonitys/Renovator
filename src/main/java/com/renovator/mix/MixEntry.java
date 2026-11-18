package com.renovator.mix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** One family in a mix palette with its weight (1..100). */
public record MixEntry(ResourceLocation family, int weight) {
    public static final Codec<MixEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("family").forGetter(MixEntry::family),
            Codec.intRange(1, 100).fieldOf("weight").forGetter(MixEntry::weight)
    ).apply(i, MixEntry::new));
}
