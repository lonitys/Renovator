package com.renovator.core;

import java.math.BigInteger;
import net.minecraft.world.item.Item;

/** A player's EMC account (ProjectE in play, a fake in tests). */
public interface EmcBank {
    BigInteger emc();

    void setEmc(BigInteger value);

    /** True when the player may create this item from EMC. */
    boolean knows(Item item);

    /** EMC value of one item, 0 when it has none. */
    long value(Item item);

    /** Tell the client about the new balance. */
    void sync();
}
