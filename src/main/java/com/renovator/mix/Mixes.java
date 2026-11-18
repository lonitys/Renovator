package com.renovator.mix;

import com.renovator.config.JsonStore;
import com.renovator.family.FamilyIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Built-in mix palettes plus the player's own ({@code config/renovator/mixes/*.json}). */
public final class Mixes {
    private Mixes() {}

    public static Path dirOverride;
    private static final JsonStore<MixPalette> STORE = new JsonStore<>(MixPalette.CODEC,
            () -> dirOverride != null ? dirOverride : com.renovator.config.Folders.resolve(FMLPaths.CONFIGDIR.get().resolve("renovator"), "mixes",
                    FMLPaths.CONFIGDIR.get().resolve("stilwerk").resolve("mixes"), FMLPaths.CONFIGDIR.get().resolve("stilwerk").resolve("mischungen")));

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static MixEntry e(String family, int weight) {
        return new MixEntry(ResourceLocation.parse(family.contains(":") ? family : "minecraft:" + family), weight);
    }

    private static MixPalette mix(String id, String name, MixPattern pattern, MixEntry... entries) {
        return new MixPalette("renovator:" + id, name, List.of(entries), pattern, Optional.empty(), Direction.Axis.Y, 0.12f);
    }

    /** Built-ins whose families all exist (entries of missing mods are dropped). */
    public static List<MixPalette> builtIn() {
        List<MixPalette> raw = new ArrayList<>();
        raw.add(mix("old_mossy", "renovator.mix.old_mossy", MixPattern.GRADIENT,
                e("mossy_stone_bricks", 30), e("cracked_stone_bricks", 20), e("stone_bricks", 50)));
        raw.add(mix("castle_wall", "renovator.mix.castle_wall", MixPattern.PATCHES,
                e("stone_bricks", 50), e("cobblestone", 15), e("andesite", 15), e("tuff_bricks", 10), e("cracked_stone_bricks", 10)));
        raw.add(mix("weathered_wood", "renovator.mix.weathered_wood", MixPattern.SCATTER,
                e("spruce", 65), e("dark_oak", 20), e("oak", 15)));
        raw.add(mix("rough_deepslate", "renovator.mix.rough_deepslate", MixPattern.PATCHES,
                e("deepslate_bricks", 45), e("cracked_deepslate_bricks", 15), e("deepslate_tiles", 20), e("cobbled_deepslate", 20)));
        raw.add(mix("sandstone_worn", "renovator.mix.sandstone_worn", MixPattern.GRADIENT,
                e("sandstone", 40), e("cut_sandstone", 30), e("smooth_sandstone", 30)));
        raw.add(mix("nether_rough", "renovator.mix.nether_rough", MixPattern.SCATTER,
                e("nether_bricks", 60), e("cracked_nether_bricks", 25), e("chiseled_nether_bricks", 15)));
        raw.add(new MixPalette("renovator:framed_stone", "renovator.mix.framed_stone", List.of(e("stone_bricks", 80), e("cracked_stone_bricks", 20)),
                MixPattern.EDGES, Optional.of(mc("polished_andesite")), Direction.Axis.Y, 0.12f));
        if (ModList.get() != null && ModList.get().isLoaded("rechiseled")) {
            raw.add(mix("rechiseled_castle", "renovator.mix.rechiseled_castle", MixPattern.PATCHES,
                    e("stone_bricks", 50), e("rechiseled:stone_small_bricks", 20), e("rechiseled:stone_rotated_bricks", 15), e("cracked_stone_bricks", 15)));
            raw.add(mix("rechiseled_deepslate", "renovator.mix.rechiseled_deepslate", MixPattern.PATCHES,
                    e("cobbled_deepslate", 40), e("rechiseled:cobbled_deepslate_bricks", 30), e("rechiseled:cobbled_deepslate_large_tiles", 15), e("deepslate_bricks", 15)));
        }
        List<MixPalette> out = new ArrayList<>();
        for (MixPalette p : raw) {
            List<MixEntry> ok = p.entries().stream().filter(x -> FamilyIndex.get().byId(x.family()) != null).toList();
            if (ok.size() >= 2) out.add(new MixPalette(p.id(), p.name(), ok, p.pattern(), p.edge(), p.axis(), p.scale()));
        }
        return out;
    }

    public static List<MixPalette> user() {
        return STORE.readAll();
    }

    /** Built-ins first, user mixes override built-ins with the same id. */
    public static Map<String, MixPalette> all() {
        Map<String, MixPalette> out = new LinkedHashMap<>();
        for (MixPalette p : builtIn()) out.put(p.id(), p);
        for (MixPalette p : user()) out.put(p.id(), p);
        return out;
    }

    public static Optional<MixPalette> byId(String id) {
        return Optional.ofNullable(all().get(id));
    }

    public static Path save(MixPalette p) throws IOException {
        return STORE.write(p.id(), p);
    }

    /** Display name: built-ins store a translation key. */
    public static boolean nameIsKey(MixPalette p) {
        return p.name().startsWith("renovator.mix.");
    }
}
