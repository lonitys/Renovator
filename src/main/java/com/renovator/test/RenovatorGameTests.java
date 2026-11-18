package com.renovator.test;

import com.renovator.Renovator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class RenovatorGameTests {
    static final String T = "empty";
    /** Relative y of the template's stone floor; build from FLOOR + 1. */
    static final int FLOOR = 1;

    @GameTest(template = T)
    public static void smoke(GameTestHelper h) {
        h.assertTrue(Renovator.ARCHITECT_WAND.get() != null, "wand not registered");
        h.succeed();
    }
}
