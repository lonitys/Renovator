package com.stilwerk.swap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stilwerk.family.Shape;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Which family becomes what; per-shape entries override the family entry. Unmapped families stay. */
public record SwapMapping(Map<ResourceLocation, Target> families, Map<ResourceLocation, Map<Shape, Target>> shapes) {
    public static final SwapMapping EMPTY = new SwapMapping(Map.of(), Map.of());

    public static final Codec<SwapMapping> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Target.CODEC).optionalFieldOf("families", Map.of()).forGetter(SwapMapping::families),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.unboundedMap(Shape.CODEC, Target.CODEC))
                    .optionalFieldOf("shapes", Map.of()).forGetter(SwapMapping::shapes)
    ).apply(i, SwapMapping::new));

    public static final StreamCodec<ByteBuf, SwapMapping> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public SwapMapping {
        families = Map.copyOf(families);
        Map<ResourceLocation, Map<Shape, Target>> s = new LinkedHashMap<>();
        shapes.forEach((k, v) -> {
            if (!v.isEmpty()) s.put(k, Map.copyOf(v));
        });
        shapes = Map.copyOf(s);
    }

    public Target resolve(ResourceLocation family, Shape shape) {
        Map<Shape, Target> perShape = shapes.get(family);
        if (perShape != null) {
            Target t = perShape.get(shape);
            if (t != null) return t;
        }
        return families.getOrDefault(family, Target.keep());
    }

    public Target familyTarget(ResourceLocation family) {
        return families.getOrDefault(family, Target.keep());
    }

    public SwapMapping with(ResourceLocation family, Target t) {
        Map<ResourceLocation, Target> f = new LinkedHashMap<>(families);
        if (t.isKeep()) f.remove(family);
        else f.put(family, t);
        return new SwapMapping(f, shapes);
    }

    public SwapMapping withShape(ResourceLocation family, Shape shape, Target t) {
        Map<ResourceLocation, Map<Shape, Target>> s = new LinkedHashMap<>(shapes);
        Map<Shape, Target> perShape = new EnumMap<>(Shape.class);
        perShape.putAll(s.getOrDefault(family, Map.of()));
        perShape.put(shape, t);
        s.put(family, perShape);
        return new SwapMapping(families, s);
    }

    public SwapMapping clearShape(ResourceLocation family, Shape shape) {
        Map<ResourceLocation, Map<Shape, Target>> s = new LinkedHashMap<>(shapes);
        Map<Shape, Target> perShape = new EnumMap<>(Shape.class);
        perShape.putAll(s.getOrDefault(family, Map.of()));
        perShape.remove(shape);
        s.put(family, perShape);
        return new SwapMapping(families, s);
    }

    public boolean isEmpty() {
        return families.isEmpty() && shapes.isEmpty();
    }

    /** Ids of all mixes this mapping refers to. */
    public Set<String> mixIds() {
        Set<String> out = new HashSet<>();
        families.values().forEach(t -> t.mix().ifPresent(out::add));
        shapes.values().forEach(m -> m.values().forEach(t -> t.mix().ifPresent(out::add)));
        return out;
    }
}
