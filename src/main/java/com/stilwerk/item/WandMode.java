package com.stilwerk.item;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** What the wand does on right-click. Client-side UI state. */
public enum WandMode {
    SELECT(Items.SPYGLASS),
    SWAP(Items.BRUSH),
    BLUEPRINT(Items.MAP);

    private final Item icon;

    WandMode(Item icon) {
        this.icon = icon;
    }

    public Item icon() {
        return icon;
    }

    public String translationKey() {
        return "stilwerk.mode." + name().toLowerCase(Locale.ROOT);
    }
}
