package com.renovator.net;

import com.renovator.Renovator;
import com.renovator.core.Selection;
import com.renovator.detail.DetailTypes.Choice;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: apply chosen detail suggestions, found in a selection or around a clicked block. */
public record DetailRequest(int id, Optional<Selection> selection, Optional<BlockPos> clicked, long seed, List<Choice> choices,
                            boolean partialOk) implements CustomPacketPayload {
    public static final Type<DetailRequest> TYPE = new Type<>(Renovator.id("detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DetailRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                ByteBufCodecs.VAR_INT.encode(buf, r.id);
                ByteBufCodecs.optional(Selection.STREAM_CODEC).encode(buf, r.selection);
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, r.clicked);
                buf.writeLong(r.seed);
                Choice.STREAM_CODEC.apply(ByteBufCodecs.list(512)).encode(buf, r.choices);
                buf.writeBoolean(r.partialOk);
            },
            buf -> new DetailRequest(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.optional(Selection.STREAM_CODEC).decode(buf),
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf), buf.readLong(),
                    Choice.STREAM_CODEC.apply(ByteBufCodecs.list(512)).decode(buf), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
