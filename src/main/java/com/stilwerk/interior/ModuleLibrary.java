package com.stilwerk.interior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Vec3i;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Furniture modules and room recipes from {@code data/stilwerk/stilwerk_interior/*.json} on the class path
 * (identical on client and server). Modules of missing mods or with unknown blocks are left out.
 */
public final class ModuleLibrary {
    private ModuleLibrary() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile Map<String, List<Module>> byCategory;
    private static volatile List<RoomType> types;

    private static JsonElement read(String name) {
        try (InputStream in = ModuleLibrary.class.getResourceAsStream("/data/stilwerk/stilwerk_interior/" + name)) {
            if (in == null) throw new IllegalStateException("missing " + name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized void load() {
        if (byCategory != null) return;
        Map<String, List<Module>> cats = new LinkedHashMap<>();
        int skipped = 0;
        for (JsonElement el : read("modules.json").getAsJsonArray()) {
            JsonObject o = el.getAsJsonObject();
            List<String> requires = new ArrayList<>();
            if (o.has("requires")) for (JsonElement r : o.getAsJsonArray("requires")) requires.add(r.getAsString());
            if (!requires.stream().allMatch(ModList.get()::isLoaded)) continue;
            JsonArray size = o.getAsJsonArray("size");
            List<Module.Cell> cells = new ArrayList<>();
            for (JsonElement c : o.getAsJsonArray("blocks")) {
                JsonObject co = c.getAsJsonObject();
                JsonArray p = co.getAsJsonArray("p");
                cells.add(new Module.Cell(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt(), co.get("s").getAsString()));
            }
            Module m = new Module(o.get("id").getAsString(), o.get("category").getAsString(),
                    new Vec3i(size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt()),
                    o.get("placement").getAsString(), requires, cells, o.has("weight") ? o.get("weight").getAsInt() : 10);
            if (!m.resolvable()) {
                skipped++;
                LOG.warn("Stilwerk: interior module {} has unknown blocks, skipped", m.id());
                continue;
            }
            cats.computeIfAbsent(m.category(), k -> new ArrayList<>()).add(m);
        }
        List<RoomType> ts = new ArrayList<>();
        for (JsonElement el : read("recipes.json").getAsJsonArray()) {
            JsonObject o = el.getAsJsonObject();
            List<RoomType.Slot> slots = new ArrayList<>();
            for (JsonElement s : o.getAsJsonArray("slots")) {
                JsonObject so = s.getAsJsonObject();
                slots.add(new RoomType.Slot(so.get("category").getAsString(), so.get("min").getAsInt(), so.get("max").getAsInt(),
                        so.get("priority").getAsInt(), so.get("prefer").getAsString()));
            }
            ts.add(new RoomType(o.get("id").getAsString(), List.copyOf(slots), o.get("rug").getAsBoolean(), o.get("light").getAsBoolean()));
        }
        types = Collections.unmodifiableList(ts);
        byCategory = cats;
        int total = cats.values().stream().mapToInt(List::size).sum();
        LOG.info("Stilwerk: {} interior modules in {} categories ({} skipped), {} room types", total, cats.size(), skipped, ts.size());
    }

    /** Modules of a category; modded ones first when present. */
    public static List<Module> available(String category) {
        load();
        return byCategory.getOrDefault(category, List.of());
    }

    public static List<String> categories() {
        load();
        return List.copyOf(byCategory.keySet());
    }

    public static List<RoomType> types() {
        load();
        return types;
    }

    public static RoomType type(String id) {
        for (RoomType t : types()) if (t.id().equals(id)) return t;
        return types().get(0);
    }
}
