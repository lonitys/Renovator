package com.renovator.net;

import com.renovator.Renovator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: furnish the room around the clicked block with this room type, seed and furniture choice. */
public record InteriorRequest(int id, BlockPos clicked, Direction face, String roomType, long seed, String furniture, boolean partialOk)
        implements CustomPacketPayload {
    public static final Type<InteriorRequest> TYPE = new Type<>(Renovator.id("interior"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InteriorRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                ByteBufCodecs.VAR_INT.encode(buf, r.id);
                BlockPos.STREAM_CODEC.encode(buf, r.clicked);
                Direction.STREAM_CODEC.encode(buf, r.face);
                ByteBufCodecs.STRING_UTF8.encode(buf, r.roomType);
                buf.writeLong(r.seed);
                ByteBufCodecs.STRING_UTF8.encode(buf, r.furniture);
                buf.writeBoolean(r.partialOk);
            },
            buf -> new InteriorRequest(ByteBufCodecs.VAR_INT.decode(buf), BlockPos.STREAM_CODEC.decode(buf), Direction.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readLong(), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
