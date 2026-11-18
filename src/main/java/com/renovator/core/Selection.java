package com.renovator.core;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the player marked. A flood selection travels as (start, radius) and is resolved on each side
 * from that side's world with the same algorithm.
 */
public sealed interface Selection permits Selection.Box, Selection.Flood {

    record Box(BlockPos a, BlockPos b) implements Selection {
        public BoundingBox bounds() {
            return BoundingBox.fromCorners(a, b);
        }

        @Override
        public BoundingBox roughBounds() {
            return bounds();
        }

        @Override
        public ResolvedSelection resolve(BlockGetter level, int limit) {
            return ResolvedSelection.ofBox(bounds(), limit);
        }
    }

    record Flood(BlockPos start, int radius) implements Selection {
        @Override
        public BoundingBox roughBounds() {
            return new BoundingBox(start.getX() - radius, start.getY() - radius, start.getZ() - radius,
                    start.getX() + radius, start.getY() + radius, start.getZ() + radius);
        }

        @Override
        public ResolvedSelection resolve(BlockGetter level, int limit) {
            return AutoSelect.flood(level, start, radius, limit);
        }
    }

    /** Exact bounds for a box, the largest possible extent for a flood. */
    BoundingBox roughBounds();

    ResolvedSelection resolve(BlockGetter level, int limit);

    StreamCodec<ByteBuf, Selection> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                if (s instanceof Box box) {
                    buf.writeByte(0);
                    BlockPos.STREAM_CODEC.encode(buf, box.a());
                    BlockPos.STREAM_CODEC.encode(buf, box.b());
                } else if (s instanceof Flood f) {
                    buf.writeByte(1);
                    BlockPos.STREAM_CODEC.encode(buf, f.start());
                    ByteBufCodecs.VAR_INT.encode(buf, f.radius());
                }
            },
            buf -> {
                byte kind = buf.readByte();
                if (kind == 0) return new Box(BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf));
                if (kind == 1) return new Flood(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
                throw new IllegalArgumentException("unknown selection kind " + kind);
            });
}
