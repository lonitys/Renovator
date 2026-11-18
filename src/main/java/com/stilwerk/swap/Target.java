package com.stilwerk.swap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** What a family (or one of its shapes) turns into: nothing, another family, or a mix palette. */
public record Target(Kind kind, Optional<ResourceLocation> family, Optional<String> mix) {
    public enum Kind implements StringRepresentable {
        KEEP, FAMILY, MIX;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(Target::kind),
            ResourceLocation.CODEC.optionalFieldOf("family").forGetter(Target::family),
            Codec.STRING.optionalFieldOf("mix").forGetter(Target::mix)
    ).apply(i, Target::new));

    private static final Target KEEP = new Target(Kind.KEEP, Optional.empty(), Optional.empty());

    public static Target keep() {
        return KEEP;
    }

    public static Target family(ResourceLocation f) {
        return new Target(Kind.FAMILY, Optional.of(f), Optional.empty());
    }

    public static Target mix(String id) {
        return new Target(Kind.MIX, Optional.empty(), Optional.of(id));
    }

    public boolean isKeep() {
        return kind == Kind.KEEP;
    }
}
