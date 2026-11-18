package com.renovator.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences. */
public final class ClientConfig {
    private ClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue GHOST_ALPHA;
    private static final ModConfigSpec.IntValue MAX_GHOST_BLOCKS;
    private static final ModConfigSpec.IntValue FLOOD_RADIUS;
    private static final ModConfigSpec.ConfigValue<String> INTERIOR_FURNITURE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        GHOST_ALPHA = b.comment("Opacity of the preview blocks.").defineInRange("ghostAlpha", 0.6, 0.1, 1.0);
        MAX_GHOST_BLOCKS = b.comment("Maximum preview blocks drawn (bigger previews show only part).")
                .defineInRange("maxGhostBlocks", 200_000, 1_000, 2_000_000);
        FLOOD_RADIUS = b.comment("Radius of the automatic building selection (the server caps it with its own setting).")
                .defineInRange("floodRadius", 128, 4, 512);
        INTERIOR_FURNITURE = b.comment("Furniture for the interior designer: mods (all installed furniture mods), a mod id, or vanilla.")
                .define("interiorFurniture", "mods");
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

    public static String interiorFurniture() {
        return SPEC.isLoaded() ? INTERIOR_FURNITURE.get() : INTERIOR_FURNITURE.getDefault();
    }

    public static void setInteriorFurniture(String choice) {
        if (!SPEC.isLoaded()) return;
        INTERIOR_FURNITURE.set(choice);
        INTERIOR_FURNITURE.save();
    }
}
