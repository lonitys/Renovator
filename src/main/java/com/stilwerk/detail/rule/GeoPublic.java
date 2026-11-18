package com.stilwerk.detail.rule;

import net.minecraft.world.level.block.state.BlockState;

/** The few geometry helpers other packages need. */
public final class GeoPublic {
    private GeoPublic() {}

    public static boolean isGlass(BlockState s) {
        return Geo.isGlass(s);
    }
}
