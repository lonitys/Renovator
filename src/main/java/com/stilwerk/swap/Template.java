package com.stilwerk.swap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** A saved set of family mappings, e.g. "Japanese → Nordic". Built-ins use a translation key as name. */
public record Template(String id, String name, Map<ResourceLocation, Target> mapping) {
    public static final Codec<Template> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Template::id),
            Codec.STRING.fieldOf("name").forGetter(Template::name),
            Codec.unboundedMap(ResourceLocation.CODEC, Target.CODEC).fieldOf("mapping").forGetter(Template::mapping)
    ).apply(i, Template::new));

    public static final StreamCodec<ByteBuf, Template> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Template {
        mapping = Map.copyOf(new LinkedHashMap<>(mapping));
    }

    /** Applies the entries for families that are present; other families keep their current mapping. */
    public SwapMapping applyTo(SwapMapping base, Collection<ResourceLocation> present) {
        SwapMapping out = base;
        for (Map.Entry<ResourceLocation, Target> e : mapping.entrySet()) {
            if (present.contains(e.getKey())) out = out.with(e.getKey(), e.getValue());
        }
        return out;
    }

    public boolean nameIsKey() {
        return name.startsWith("stilwerk.template.");
    }
}
