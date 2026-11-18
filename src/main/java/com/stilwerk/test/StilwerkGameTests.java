package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class StilwerkGameTests {
    static final String T = "empty";
    /** Relative y of the template's stone floor; build from FLOOR + 1. */
    static final int FLOOR = 1;

    @GameTest(template = T)
    public static void smoke(GameTestHelper h) {
        h.assertTrue(Stilwerk.ARCHITECT_WAND.get() != null, "wand not registered");
        h.succeed();
    }
}
