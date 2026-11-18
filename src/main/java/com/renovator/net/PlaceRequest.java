package com.renovator.net;

import com.renovator.Renovator;
import com.renovator.mix.MixPalette;
import com.renovator.swap.SwapMapping;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Client → server: place the uploaded blueprint with this hash. */
public record PlaceRequest(int id, byte[] hash, BlockPos origin, Rotation rotation, Mirror mirror, SwapMapping mapping,
                           List<MixPalette> mixes, long seed, boolean overwrite, BuildMode mode) implements CustomPacketPayload {
    public static final Type<PlaceRequest> TYPE = new Type<>(Renovator.id("place"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                ByteBufCodecs.VAR_INT.encode(buf, r.id);
                ByteBufCodecs.byteArray(64).encode(buf, r.hash);
                BlockPos.STREAM_CODEC.encode(buf, r.origin);
                buf.writeEnum(r.rotation);
                buf.writeEnum(r.mirror);
                SwapMapping.STREAM_CODEC.encode(buf, r.mapping);
                MixPalette.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, r.mixes);
                buf.writeLong(r.seed);
                buf.writeBoolean(r.overwrite);
                BuildMode.STREAM_CODEC.encode(buf, r.mode);
            },
            buf -> new PlaceRequest(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.byteArray(64).decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                    buf.readEnum(Rotation.class), buf.readEnum(Mirror.class), SwapMapping.STREAM_CODEC.decode(buf),
                    MixPalette.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf), buf.readLong(), buf.readBoolean(), BuildMode.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
