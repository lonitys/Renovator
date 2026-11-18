package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.interior.FurnitureChoice;
import com.renovator.interior.InteriorGenerator;
import com.renovator.interior.ModuleLibrary;
import com.renovator.interior.Room;
import com.renovator.interior.WoodAdapter;
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
@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class InteriorModTests {
    private static final String T = RenovatorGameTests.T;

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

    static java.util.Set<String> namespaces(InteriorGenerator.Result r) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (var c : r.changes().changes()) out.add(BuiltInRegistries.BLOCK.getKey(c.newState().getBlock()).getNamespace());
        return out;
    }

    @GameTest(template = T)
    public static void furnitureChoiceIsRespected(GameTestHelper h) {
        InteriorTests.room(h, 9, 9, 4);
        Room room = InteriorTests.detect(h);
        java.util.List<String> options = FurnitureChoice.options();
        h.assertTrue(options.get(options.size() - 1).equals(FurnitureChoice.VANILLA), "vanilla is always offered: " + options);
        for (String type : new String[]{"bedroom", "kitchen", "living", "dining", "bath"}) {
            var vanilla = InteriorGenerator.generate(h.getLevel(), room, ModuleLibrary.type(type), 3, InteriorTests.wood("spruce"), FurnitureChoice.VANILLA);
            h.assertTrue(namespaces(vanilla).equals(java.util.Set.of("minecraft")), type + " vanilla only, got " + namespaces(vanilla));
            h.assertFalse(vanilla.placed().stream().filter(id -> !id.equals("rug") && !id.equals("light")).toList().isEmpty(), type + " still furnished");
            for (String mod : options) {
                if (mod.equals(FurnitureChoice.VANILLA) || mod.equals(FurnitureChoice.MODS)) continue;
                var only = InteriorGenerator.generate(h.getLevel(), room, ModuleLibrary.type(type), 3, InteriorTests.wood("spruce"), mod);
                java.util.Set<String> ns = namespaces(only);
                ns.remove("minecraft");
                h.assertTrue(ns.isEmpty() || ns.equals(java.util.Set.of(mod)), type + " with " + mod + " uses " + ns);
            }
        }
        h.assertTrue(FurnitureChoice.normalize("not_a_mod").equals(options.get(0)), "unknown choice falls back to the default");
        h.succeed();
    }
}
