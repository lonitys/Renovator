package com.stilwerk.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences. */
public final class ClientConfig {
    private ClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue GHOST_ALPHA;
    private static final ModConfigSpec.IntValue MAX_GHOST_BLOCKS;
    private static final ModConfigSpec.IntValue FLOOD_RADIUS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        GHOST_ALPHA = b.comment("Opacity of the preview blocks.").defineInRange("ghostAlpha", 0.6, 0.1, 1.0);
        MAX_GHOST_BLOCKS = b.comment("Maximum preview blocks drawn (bigger previews show only part).")
                .defineInRange("maxGhostBlocks", 60_000, 1_000, 500_000);
        FLOOD_RADIUS = b.comment("Radius of the automatic building selection (the server caps it with its own setting).")
                .defineInRange("floodRadius", 48, 4, 256);
        SPEC = b.build();
    }

    public static float ghostAlpha() {
        return (SPEC.isLoaded() ? GHOST_ALPHA.get() : GHOST_ALPHA.getDefault()).floatValue();
    }

    public static int maxGhostBlocks() {
        return SPEC.isLoaded() ? MAX_GHOST_BLOCKS.get() : MAX_GHOST_BLOCKS.getDefault();
    }

    public static int floodRadius() {
        return SPEC.isLoaded() ? FLOOD_RADIUS.get() : FLOOD_RADIUS.getDefault();
    }
}
