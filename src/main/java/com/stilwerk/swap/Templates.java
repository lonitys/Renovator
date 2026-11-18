package com.stilwerk.swap;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.stilwerk.config.JsonStore;
import com.stilwerk.family.FamilyIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/** Built-in style templates, the player's own ({@code config/stilwerk/vorlagen}) and server-wide datapack ones. */
public final class Templates {
    private Templates() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** Plank tones from light to dark; darken/lighten move one step. */
    public static final List<String> WOOD_ORDER = List.of("birch", "bamboo", "oak", "jungle", "acacia", "cherry", "spruce", "mangrove", "dark_oak");

    public static Path dirOverride;
    private static final JsonStore<Template> STORE = new JsonStore<>(Template.CODEC,
            () -> dirOverride != null ? dirOverride : FMLPaths.CONFIGDIR.get().resolve("stilwerk").resolve("vorlagen"));

    /** Datapack templates: loaded on the server, synced to clients. */
    private static volatile List<Template> datapack = List.of();

    private static ResourceLocation mc(String p) {
        return ResourceLocation.withDefaultNamespace(p);
    }

    private static Template of(String id, String... pairs) {
        Map<ResourceLocation, Target> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String to = pairs[i + 1];
            m.put(mc(pairs[i]), to.startsWith("mix:") ? Target.mix(to.substring(4)) : Target.family(mc(to)));
        }
        return new Template("stilwerk:" + id, "stilwerk.template." + id, m);
    }

    private static Template woodStep(String id, int step) {
        Map<ResourceLocation, Target> m = new LinkedHashMap<>();
        for (int i = 0; i < WOOD_ORDER.size(); i++) {
            int j = i + step;
            if (j >= 0 && j < WOOD_ORDER.size()) m.put(mc(WOOD_ORDER.get(i)), Target.family(mc(WOOD_ORDER.get(j))));
        }
        return new Template("stilwerk:" + id, "stilwerk.template." + id, m);
    }

    public static List<Template> builtIn() {
        List<Template> raw = List.of(
                of("japanese_to_nordic", "acacia", "spruce", "jungle", "dark_oak", "cherry", "spruce", "oak", "spruce", "bamboo", "spruce"),
                woodStep("darken_wood", 1),
                woodStep("lighten_wood", -1),
                of("stone_to_deepslate", "cobblestone", "cobbled_deepslate", "stone_bricks", "deepslate_bricks",
                        "mossy_stone_bricks", "deepslate_tiles", "cracked_stone_bricks", "cracked_deepslate_bricks",
                        "polished_andesite", "polished_deepslate", "smooth_stone", "polished_deepslate",
                        "chiseled_stone_bricks", "chiseled_deepslate"),
                of("desert_to_snow", "sandstone", "smooth_quartz", "cut_sandstone", "quartz_bricks", "smooth_sandstone", "smooth_quartz",
                        "chiseled_sandstone", "chiseled_quartz_block", "acacia", "spruce", "terracotta", "white_terracotta"),
                of("medieval_to_dark", "oak", "dark_oak", "spruce", "dark_oak", "stone_bricks", "polished_blackstone_bricks",
                        "cobblestone", "blackstone"),
                of("oak_to_cherry", "oak", "cherry"),
                of("stone_to_tuff", "stone_bricks", "tuff_bricks", "cobblestone", "polished_tuff"),
                of("brick_to_mud", "bricks", "mud_bricks"),
                of("everything_mossy", "stone_bricks", "mix:stilwerk:old_mossy", "cobblestone", "mossy_cobblestone"));
        List<Template> out = new ArrayList<>();
        for (Template t : raw) out.add(validated(t));
        return out;
    }

    /** Drops entries whose families do not exist (missing mods, typos). */
    static Template validated(Template t) {
        Map<ResourceLocation, Target> m = new LinkedHashMap<>();
        t.mapping().forEach((from, to) -> {
            boolean ok = FamilyIndex.get().byId(from) != null
                    && (to.kind() != Target.Kind.FAMILY || to.family().map(f -> FamilyIndex.get().byId(f) != null).orElse(false));
            if (ok) m.put(from, to);
            else LOG.debug("Stilwerk: template {} drops {} → {}", t.id(), from, to);
        });
        return new Template(t.id(), t.name(), m);
    }

    public static List<Template> user() {
        List<Template> out = new ArrayList<>();
        for (Template t : STORE.readAll()) out.add(validated(t));
        return out;
    }

    public static List<Template> datapack() {
        return datapack;
    }

    /** Client: templates the server sent. */
    public static void setDatapack(List<Template> list) {
        datapack = List.copyOf(list);
    }

    /** Built-in, then datapack, then the player's own. */
    public static List<Template> all() {
        List<Template> out = new ArrayList<>(builtIn());
        out.addAll(datapack);
        out.addAll(user());
        return out;
    }

    public static Path save(Template t) throws IOException {
        return STORE.write(t.id(), t);
    }

    /** {@code data/<ns>/stilwerk/templates/*.json} */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new com.google.gson.Gson(), "stilwerk/templates");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Template> list = new ArrayList<>();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    Template.CODEC.parse(JsonOps.INSTANCE, e.getValue())
                            .resultOrPartial(err -> LOG.warn("Stilwerk: template {}: {}", e.getKey(), err))
                            .ifPresent(t -> list.add(validated(t))));
            datapack = List.copyOf(list);
        }
    }
}
