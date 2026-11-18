package com.stilwerk.core;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/** What a block costs in survival: which item and how many of it. */
public final class Costs {
    private Costs() {}

    private static final IntegerProperty[] COUNTED = {
            BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT
    };

    /** Item that pays for this state; {@link Items#AIR} when it cannot be obtained. */
    public static Item item(BlockState s) {
        return s.getBlock().asItem();
    }

    /** How many items one block of this state costs (0 for air and for the secondary half of two-block blocks). */
    public static int count(BlockState s) {
        if (s.isAir() || item(s) == Items.AIR) return 0;
        if (!Groups.isPrimary(s)) return 0;
        if (s.hasProperty(SlabBlock.TYPE) && s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return 2;
        for (IntegerProperty p : COUNTED) if (s.hasProperty(p)) return s.getValue(p);
        if (s.getBlock() instanceof SnowLayerBlock) return s.getValue(SnowLayerBlock.LAYERS);
        return 1;
    }

    /** Summed item counts. */
    public static Object2IntOpenHashMap<Item> tally(Iterable<BlockState> states) {
        Object2IntOpenHashMap<Item> out = new Object2IntOpenHashMap<>();
        for (BlockState s : states) {
            int n = count(s);
            if (n > 0) out.addTo(item(s), n);
        }
        return out;
    }
}
