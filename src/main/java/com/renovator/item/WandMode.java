package com.renovator.item;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** What the wand does on right-click. Client-side UI state. */
public enum WandMode {
    SELECT(Items.SPYGLASS),
    SWAP(Items.BRUSH),
    BLUEPRINT(Items.MAP),
    DETAILS(Items.FLOWER_POT),
    INTERIOR(Items.RED_BED),
    PROJECTIONS(Items.LIGHT_BLUE_STAINED_GLASS);

    private final Item icon;

    WandMode(Item icon) {
        this.icon = icon;
    }

    public Item icon() {
        return icon;
    }

    public String translationKey() {
        return "renovator.mode." + name().toLowerCase(Locale.ROOT);
    }
}
