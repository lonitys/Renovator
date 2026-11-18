package com.stilwerk.net;

import com.stilwerk.Stilwerk;
import com.stilwerk.core.Selection;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.swap.SwapMapping;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: apply a style swap to a selection. The server plans it again from its own world. */
public record SwapRequest(int id, Selection selection, SwapMapping mapping, List<MixPalette> mixes, long seed,
                          boolean includeNature, boolean partialOk) implements CustomPacketPayload {
    public static final Type<SwapRequest> TYPE = new Type<>(Stilwerk.id("swap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwapRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                ByteBufCodecs.VAR_INT.encode(buf, r.id);
                Selection.STREAM_CODEC.encode(buf, r.selection);
                SwapMapping.STREAM_CODEC.encode(buf, r.mapping);
                MixPalette.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, r.mixes);
                buf.writeLong(r.seed);
                buf.writeBoolean(r.includeNature);
                buf.writeBoolean(r.partialOk);
            },
            buf -> new SwapRequest(ByteBufCodecs.VAR_INT.decode(buf), Selection.STREAM_CODEC.decode(buf), SwapMapping.STREAM_CODEC.decode(buf),
                    MixPalette.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf), buf.readLong(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
