package com.renovator.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** How the server should treat an action once it is planned. */
public enum BuildMode {
    /** Creative: build now. Survival: show the build dialog (materials, build / as projection / cancel). */
    ASK,
    /** Build now, as far as the materials go. */
    PARTIAL,
    /** Do not build: keep it as a projection to build by hand or layer by layer. */
    PROJECTION;

    public static final StreamCodec<ByteBuf, BuildMode> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal);
}
