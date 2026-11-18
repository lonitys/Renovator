package com.stilwerk.mix;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** How a mix palette distributes its blocks. */
public enum MixPattern implements StringRepresentable {
    /** Weighted random per block. */
    SCATTER,
    /** Entries in list order along an axis (first at the bottom), with a jittered border. */
    GRADIENT,
    /** Natural-looking patches from simplex noise. */
    PATCHES,
    /** Edges and corners get the edge block, the rest is scattered. */
    EDGES;

    public static final Codec<MixPattern> CODEC = StringRepresentable.fromEnum(MixPattern::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "stilwerk.pattern." + getSerializedName();
    }
}
