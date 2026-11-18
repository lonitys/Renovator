package com.stilwerk.mix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

/**
 * A weighted list of families plus a distribution pattern. The first entry is the base: shapes a picked
 * family lacks fall back to it. {@link #pick} is a pure function of (seed, position, context).
 */
public record MixPalette(String id, String name, List<MixEntry> entries, MixPattern pattern,
                         Optional<ResourceLocation> edge, Direction.Axis axis, float scale) {

    public static final Codec<MixPalette> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(MixPalette::id),
            Codec.STRING.fieldOf("name").forGetter(MixPalette::name),
            ExtraCodecs.nonEmptyList(MixEntry.CODEC.listOf()).fieldOf("entries").forGetter(MixPalette::entries),
            MixPattern.CODEC.fieldOf("pattern").forGetter(MixPalette::pattern),
            ResourceLocation.CODEC.optionalFieldOf("edge").forGetter(MixPalette::edge),
            Direction.Axis.CODEC.optionalFieldOf("axis", Direction.Axis.Y).forGetter(MixPalette::axis),
            Codec.floatRange(0.01f, 1f).optionalFieldOf("scale", 0.12f).forGetter(MixPalette::scale)
    ).apply(i, MixPalette::new));

    public static final StreamCodec<ByteBuf, MixPalette> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public MixPalette {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException("mix palette without entries");
    }

    public ResourceLocation base() {
        return entries.get(0).family();
    }

    public ResourceLocation pick(long seed, BlockPos pos, PickContext ctx) {
        return switch (pattern) {
            case SCATTER -> byWeight(Hash.unit(seed, pos, 1));
            case GRADIENT -> {
                double t = ctx.along(axis, pos);
                yield byWeight(Mth.clamp(t + (Hash.unit(seed, pos, 2) - 0.5) * 0.35, 0, 0.999999));
            }
            case PATCHES -> {
                double n = ctx.noise(seed).getValue(pos.getX() * scale, pos.getY() * scale, pos.getZ() * scale);
                yield byWeight(Mth.clamp((n + 1) / 2 + (Hash.unit(seed, pos, 3) - 0.5) * 0.1, 0, 0.999999));
            }
            case EDGES -> edge.isPresent() && ctx.isEdge(pos) ? edge.get() : byWeight(Hash.unit(seed, pos, 4));
        };
    }

    /** Maps u in [0,1) onto the entries by cumulative weight in list order. */
    private ResourceLocation byWeight(double u) {
        int total = 0;
        for (MixEntry e : entries) total += e.weight();
        double target = u * total;
        int acc = 0;
        for (MixEntry e : entries) {
            acc += e.weight();
            if (target < acc) return e.family();
        }
        return entries.get(entries.size() - 1).family();
    }

    /** Every family this palette may produce. */
    public List<ResourceLocation> families() {
        List<ResourceLocation> out = new java.util.ArrayList<>();
        for (MixEntry e : entries) out.add(e.family());
        edge.ifPresent(out::add);
        return out;
    }
}
