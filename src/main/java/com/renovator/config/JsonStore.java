package com.renovator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;

/** Reads and writes codec-backed JSON files in a config folder (user templates and mixes). */
public final class JsonStore<T> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Codec<T> codec;
    private final java.util.function.Supplier<Path> dir;

    public JsonStore(Codec<T> codec, java.util.function.Supplier<Path> dir) {
        this.codec = codec;
        this.dir = dir;
    }

    public Path dir() {
        return dir.get();
    }

    /** All valid files, sorted by file name; broken files are logged and skipped. */
    public List<T> readAll() {
        List<T> out = new ArrayList<>();
        Path d = dir.get();
        if (!Files.isDirectory(d)) return out;
        try (Stream<Path> files = Files.list(d)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    JsonElement json = JsonParser.parseReader(r);
                    codec.parse(JsonOps.INSTANCE, json).resultOrPartial(err -> LOG.warn("Renovator: {}: {}", f, err)).ifPresent(out::add);
                } catch (IOException | RuntimeException ex) {
                    LOG.warn("Renovator: could not read {}", f, ex);
                }
            }
        } catch (IOException ex) {
            LOG.warn("Renovator: could not list {}", d, ex);
        }
        return out;
    }

    /** Writes {@code <fileName(id)>.json}; returns the file. */
    public Path write(String id, T value) throws IOException {
        Path d = dir.get();
        Files.createDirectories(d);
        Path f = d.resolve(fileName(id) + ".json");
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(IOException::new);
        try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            GSON.toJson(json, w);
        }
        return f;
    }

    /** A safe file name for an id or display name. */
    public static String fileName(String id) {
        String s = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
        s = s.replaceAll("_+", "_").replaceAll("^_|_$", "");
        return s.isEmpty() ? "unnamed" : s;
    }
}
