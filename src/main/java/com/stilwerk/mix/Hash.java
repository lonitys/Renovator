package com.stilwerk.mix;

import net.minecraft.core.BlockPos;

/** Deterministic per-position randomness (identical on client and server). */
public final class Hash {
    private Hash() {}

    /** splitmix64 finaliser. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** Uniform double in [0, 1) for (seed, position, salt). */
    public static double unit(long seed, BlockPos p, int salt) {
        return (mix(seed ^ mix(p.asLong() + 0x9E3779B97F4A7C15L * salt)) >>> 11) * 0x1.0p-53;
    }
}
