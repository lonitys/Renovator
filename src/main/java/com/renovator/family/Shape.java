package com.renovator.family;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The form a block takes within its material family. */
public enum Shape implements StringRepresentable {
    FULL, STAIRS, SLAB, WALL, FENCE, FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE,
    LOG, WOOD, STRIPPED_LOG, STRIPPED_WOOD, SIGN, WALL_SIGN, HANGING_SIGN, WALL_HANGING_SIGN, PANE;

    public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
    public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Shape::ordinal);

    /** Ordered substitutes when a target family lacks this shape (the shape itself first). */
    public List<Shape> chain() {
        return switch (this) {
            case FENCE -> List.of(FENCE, WALL);
            case WALL -> List.of(WALL, FENCE);
            case LOG -> List.of(LOG, FULL);
            case WOOD -> List.of(WOOD, LOG, FULL);
            case STRIPPED_LOG -> List.of(STRIPPED_LOG, LOG, FULL);
            case STRIPPED_WOOD -> List.of(STRIPPED_WOOD, STRIPPED_LOG, WOOD, LOG, FULL);
            default -> List.of(this);
        };
    }

    @Override
    public String getSerializedName() {
        return name();
    }

    public String translationKey() {
        return "renovator.shape." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
