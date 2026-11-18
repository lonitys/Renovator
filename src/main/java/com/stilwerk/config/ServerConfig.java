package com.stilwerk.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world limits. Accessors fall back to the defaults while the config is not loaded. */
public final class ServerConfig {
    private ServerConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue MAX_BLOCKS_SURVIVAL;
    private static final ModConfigSpec.IntValue MAX_BLOCKS_CREATIVE;
    private static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    private static final ModConfigSpec.IntValue UNDO_STEPS;
    private static final ModConfigSpec.IntValue UNDO_MAX_BLOCKS;
    private static final ModConfigSpec.IntValue MAX_UPLOAD_BYTES;
    private static final ModConfigSpec.IntValue FLOOD_RADIUS;
    private static final ModConfigSpec.BooleanValue USE_PROJECTE;

    /** Test overrides (GameTests only); negative = use config. */
    public static int budgetOverride = -1;
    public static int maxBlocksOverride = -1;
    public static int undoStepsOverride = -1;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("limits");
        MAX_BLOCKS_SURVIVAL = b.comment("Maximum blocks one operation may change in survival; 0 = no limit (materials are the limit).")
                .defineInRange("maxBlocksSurvival", 0, 0, Integer.MAX_VALUE);
        MAX_BLOCKS_CREATIVE = b.comment("Maximum blocks one operation may change in creative; 0 = no limit.")
                .defineInRange("maxBlocksCreative", 0, 0, Integer.MAX_VALUE);
        BLOCKS_PER_TICK = b.comment("Blocks placed per server tick while applying (spreads big jobs to avoid lag).")
                .defineInRange("blocksPerTick", 4_000, 100, 100_000);
        MAX_UPLOAD_BYTES = b.comment("Maximum compressed size of an uploaded blueprint in bytes.")
                .defineInRange("maxUploadBytes", 64 * 1024 * 1024, 64 * 1024, 256 * 1024 * 1024);
        FLOOD_RADIUS = b.comment("Radius of the automatic building selection (it follows only the building, so a large radius is cheap).")
                .defineInRange("floodRadius", 128, 4, 512);
        b.pop();
        b.push("undo");
        UNDO_STEPS = b.comment("Undo steps kept per player.").defineInRange("undoSteps", 20, 0, 100);
        UNDO_MAX_BLOCKS = b.comment("Total blocks kept in one player's undo history; oldest steps are dropped first.")
                .defineInRange("undoMaxBlocks", 1_000_000, 1_000, 20_000_000);
        b.pop();
        b.push("compat");
        USE_PROJECTE = b.comment("Pay survival costs with ProjectE EMC (learned items only) when ProjectE is installed.")
                .define("useProjectE", true);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    /** Blocks one operation may change; {@link Integer#MAX_VALUE} when unlimited (the default). */
    public static int maxBlocks(boolean creative) {
        int v = maxBlocksOverride >= 0 ? maxBlocksOverride : get(creative ? MAX_BLOCKS_CREATIVE : MAX_BLOCKS_SURVIVAL);
        return v <= 0 ? Integer.MAX_VALUE : v;
    }

    public static int blocksPerTick() {
        return budgetOverride > 0 ? budgetOverride : get(BLOCKS_PER_TICK);
    }

    public static int undoSteps() {
        return undoStepsOverride >= 0 ? undoStepsOverride : get(UNDO_STEPS);
    }

    public static int undoMaxBlocks() {
        return get(UNDO_MAX_BLOCKS);
    }

    public static int maxUploadBytes() {
        return get(MAX_UPLOAD_BYTES);
    }

    public static int floodRadius() {
        return get(FLOOD_RADIUS);
    }

    public static boolean useProjectE() {
        return SPEC.isLoaded() ? USE_PROJECTE.get() : USE_PROJECTE.getDefault();
    }
}
