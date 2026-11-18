package com.stilwerk.client;

import com.mojang.logging.LogUtils;
import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintIO;
import com.stilwerk.blueprint.importer.ImportResult;
import com.stilwerk.blueprint.importer.Importers;
import com.stilwerk.config.JsonStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** The blueprint folder ({@code <game dir>/stilwerk/blueprints}): our .nbt files plus any imported schematics. */
public final class ClientLibrary {
    private ClientLibrary() {}

    private static final Logger LOG = LogUtils.getLogger();

    public record Entry(Path file, @Nullable Blueprint blueprint, List<String> warnings, @Nullable String error, long modified) {
        public String name() {
            if (blueprint != null && !blueprint.meta().name().isBlank()) return blueprint.meta().name();
            return Importers.baseName(file);
        }
    }

    private static final Map<Path, Entry> CACHE = new HashMap<>();

    public static Path dir() {
        Path d = com.stilwerk.config.Folders.resolve(Minecraft.getInstance().gameDirectory.toPath().resolve("stilwerk"), "blueprints", "bauplaene");
        try {
            Files.createDirectories(d);
        } catch (IOException e) {
            LOG.warn("Stilwerk: cannot create {}", d, e);
        }
        return d;
    }

    /** All supported files, newest first; unchanged files come from the cache. */
    public static synchronized List<Entry> scan() {
        List<Entry> out = new ArrayList<>();
        Map<Path, Entry> seen = new HashMap<>();
        try (Stream<Path> files = Files.list(dir())) {
            for (Path f : files.filter(Files::isRegularFile).filter(Importers::supported).toList()) {
                long mod = Files.getLastModifiedTime(f).toMillis();
                Entry e = CACHE.get(f);
                if (e == null || e.modified() != mod) e = load(f, mod);
                seen.put(f, e);
                out.add(e);
            }
        } catch (IOException ex) {
            LOG.warn("Stilwerk: cannot list blueprints", ex);
        }
        CACHE.keySet().removeIf(p -> !seen.containsKey(p));
        CACHE.putAll(seen);
        out.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        return out;
    }

    private static Entry load(Path f, long mod) {
        try {
            ImportResult r = Importers.load(f, BuiltInRegistries.BLOCK.asLookup());
            return new Entry(f, r.blueprint(), r.warnings(), null, mod);
        } catch (IOException | RuntimeException ex) {
            return new Entry(f, null, List.of(), ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), mod);
        }
    }

    /** Saves as {@code <name>.nbt} (numbered if taken). */
    public static synchronized Path save(Blueprint bp) throws IOException {
        Path f = unique(JsonStore.fileName(bp.meta().name()), ".nbt");
        BlueprintIO.write(bp, f);
        return f;
    }

    private static Path unique(String base, String ext) {
        Path d = dir();
        Path f = d.resolve(base + ext);
        for (int i = 2; Files.exists(f); i++) f = d.resolve(base + "_" + i + ext);
        return f;
    }

    public static synchronized void rename(Entry e, String name) throws IOException {
        String ext = "." + Importers.extension(e.file());
        Path target = unique(JsonStore.fileName(name), ext);
        if (e.blueprint() != null && ext.equals(".nbt")) {
            BlueprintIO.write(e.blueprint().withMeta(e.blueprint().meta().withName(name)), target);
            Files.deleteIfExists(e.file());
        } else {
            Files.move(e.file(), target);
        }
        CACHE.remove(e.file());
    }

    public static synchronized void delete(Entry e) throws IOException {
        Files.deleteIfExists(e.file());
        CACHE.remove(e.file());
    }

    /** Copies dropped files of supported types into the folder; returns how many were imported. */
    public static synchronized int importFiles(List<Path> dropped) {
        int n = 0;
        for (Path p : dropped) {
            if (!Files.isRegularFile(p) || !Importers.supported(p)) continue;
            try {
                Files.copy(p, unique(JsonStore.fileName(Importers.baseName(p)), "." + Importers.extension(p)), StandardCopyOption.COPY_ATTRIBUTES);
                n++;
            } catch (IOException ex) {
                LOG.warn("Stilwerk: import of {} failed", p, ex);
            }
        }
        return n;
    }
}
