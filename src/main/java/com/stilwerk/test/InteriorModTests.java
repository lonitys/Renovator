package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.interior.InteriorGenerator;
import com.stilwerk.interior.ModuleLibrary;
import com.stilwerk.interior.Room;
import com.stilwerk.interior.WoodAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Only meaningful with -PwithMods (Macaw's Furniture, Refurbished Furniture + Framework); otherwise they pass trivially. */
@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class InteriorModTests {
    private static final String T = StilwerkGameTests.T;

    @GameTest(template = T)
    public static void macawWoodSwap(GameTestHelper h) {
        if (!ModList.get().isLoaded("mcwfurnitures")) {
            h.succeed();
            return;
        }
        BlockState chair = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mcwfurnitures", "oak_chair")).defaultBlockState();
        BlockState spruce = WoodAdapter.adapt(chair, InteriorTests.wood("spruce"));
        h.assertTrue(BuiltInRegistries.BLOCK.getKey(spruce.getBlock()).getPath().equals("spruce_chair"), "oak_chair → spruce_chair: " + spruce);
        BlockState dark = WoodAdapter.adapt(chair, InteriorTests.wood("dark_oak"));
        h.assertTrue(BuiltInRegistries.BLOCK.getKey(dark.getBlock()).getPath().equals("dark_oak_chair"), "oak_chair → dark_oak_chair: " + dark);
        h.assertTrue(ModuleLibrary.available("wardrobe").stream().anyMatch(m -> m.modded()), "modded wardrobes available");
        h.succeed();
    }

    @GameTest(template = T)
    public static void modModulesPreferred(GameTestHelper h) {
        if (!ModList.get().isLoaded("mcwfurnitures") && !ModList.get().isLoaded("refurbished_furniture")) {
            h.succeed();
            return;
        }
        InteriorTests.room(h, 9, 9, 4);
        Room room = InteriorTests.detect(h);
        for (String type : new String[]{"bedroom", "kitchen", "living", "dining"}) {
            var r = InteriorGenerator.generate(h.getLevel(), room, ModuleLibrary.type(type), 3, InteriorTests.wood("spruce"));
            h.assertTrue(r.placed().stream().anyMatch(id -> id.endsWith("_mcw") || id.endsWith("_rf") || id.contains("_mcw_") || id.contains("_rf_")),
                    type + " uses mod furniture: " + r.placed());
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void allModulesResolve(GameTestHelper h) {
        // with the mods loaded, every module (vanilla and modded) must load: nothing may be skipped for unknown blocks
        int total = 0;
        for (String c : ModuleLibrary.categories()) total += ModuleLibrary.available(c).size();
        boolean mcw = ModList.get().isLoaded("mcwfurnitures"), rf = ModList.get().isLoaded("refurbished_furniture");
        int expected = mcw && rf ? 60 : !mcw && !rf ? 32 : total;
        h.assertTrue(total == expected, "modules loaded " + total + ", expected " + expected);
        h.succeed();
    }
}
