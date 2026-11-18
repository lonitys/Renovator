package com.stilwerk.family;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Datapack family definitions: {@code data/<ns>/stilwerk/families/<name>.json}
 * <pre>{ "family": "mymod:fir", "wood": true, "blocks": { "FULL": "mymod:fir_planks", "STAIRS": "mymod:fir_steps" } }</pre>
 */
public final class FamilyOverrides extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    public FamilyOverrides() {
        super(GSON, "stilwerk/families");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<FamilyIndex.Override> list = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            try {
                list.add(parse(e.getValue().getAsJsonObject()));
            } catch (RuntimeException ex) {
                LOG.warn("Stilwerk: ignoring family override {}: {}", e.getKey(), ex.getMessage());
            }
        });
        FamilyIndex.rebuild(list);
        LOG.info("Stilwerk: {} families ({} datapack overrides)", FamilyIndex.get().all().size(), list.size());
    }

    static FamilyIndex.Override parse(JsonObject o) {
        ResourceLocation family = ResourceLocation.parse(o.get("family").getAsString());
        boolean wood = o.has("wood") && o.get("wood").getAsBoolean();
        Map<Shape, ResourceLocation> blocks = new EnumMap<>(Shape.class);
        for (Map.Entry<String, JsonElement> b : o.getAsJsonObject("blocks").entrySet()) {
            blocks.put(Shape.valueOf(b.getKey().toUpperCase(Locale.ROOT)), ResourceLocation.parse(b.getValue().getAsString()));
        }
        return new FamilyIndex.Override(family, wood, blocks);
    }
}
