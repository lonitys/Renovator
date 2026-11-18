package com.stilwerk.net;

import com.stilwerk.Stilwerk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: furnish the room around the clicked block with this room type and seed. */
public record InteriorRequest(int id, BlockPos clicked, Direction face, String roomType, long seed, boolean partialOk) implements CustomPacketPayload {
    public static final Type<InteriorRequest> TYPE = new Type<>(Stilwerk.id("interior"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InteriorRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                ByteBufCodecs.VAR_INT.encode(buf, r.id);
                BlockPos.STREAM_CODEC.encode(buf, r.clicked);
                Direction.STREAM_CODEC.encode(buf, r.face);
                ByteBufCodecs.STRING_UTF8.encode(buf, r.roomType);
                buf.writeLong(r.seed);
                buf.writeBoolean(r.partialOk);
            },
            buf -> new InteriorRequest(ByteBufCodecs.VAR_INT.decode(buf), BlockPos.STREAM_CODEC.decode(buf), Direction.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readLong(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
