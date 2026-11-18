package com.renovator.core;

import com.mojang.logging.LogUtils;
import com.renovator.config.ServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/** Per-player undo stack, kept in {@code <world>/renovator/undo/<uuid>.nbt} so it survives restarts. */
public final class UndoHistory {
    private UndoHistory() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<UUID, Deque<Entry>>> CACHE = new WeakHashMap<>();

    public record Entry(ResourceKey<Level> dimension, ChangeSet applied, long time) {}

    public static synchronized void push(MinecraftServer server, UUID player, Entry e) {
        if (e.applied().isEmpty()) return;
        Deque<Entry> d = deque(server, player);
        d.addLast(e);
        int steps = ServerConfig.undoSteps();
        while (d.size() > steps) d.removeFirst();
        long total = 0;
        for (Entry x : d) total += x.applied().size();
        while (d.size() > 1 && total > ServerConfig.undoMaxBlocks()) total -= d.removeFirst().applied().size();
        write(server, player, d);
    }

    public static synchronized Optional<Entry> pop(MinecraftServer server, UUID player) {
        Deque<Entry> d = deque(server, player);
        Entry e = d.pollLast();
        if (e != null) write(server, player, d);
        return Optional.ofNullable(e);
    }

    public static synchronized int depth(MinecraftServer server, UUID player) {
        return deque(server, player).size();
    }

    /** Tests: forget the in-memory cache so the next access reads the file. */
    public static synchronized void clearCacheForTests(MinecraftServer server) {
        CACHE.remove(server);
    }

    static Path file(MinecraftServer server, UUID player) {
        return server.getWorldPath(LevelResource.ROOT).resolve("renovator").resolve("undo").resolve(player + ".nbt");
    }

    private static Deque<Entry> deque(MinecraftServer server, UUID player) {
        return CACHE.computeIfAbsent(server, s -> new HashMap<>()).computeIfAbsent(player, p -> read(server, p));
    }

    private static Deque<Entry> read(MinecraftServer server, UUID player) {
        Deque<Entry> d = new ArrayDeque<>();
        Path f = file(server, player);
        if (!Files.isRegularFile(f)) return d;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
                d.addLast(new Entry(dim, ChangeSet.load(t.getCompound("set"), BuiltInRegistries.BLOCK.asLookup()), t.getLong("time")));
            }
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Renovator: could not read undo history {}", f, ex);
        }
        return d;
    }

    private static void write(MinecraftServer server, UUID player, Deque<Entry> d) {
        Path f = file(server, player);
        try {
            if (d.isEmpty()) {
                Files.deleteIfExists(f);
                return;
            }
            ListTag list = new ListTag();
            for (Entry e : d) {
                CompoundTag t = new CompoundTag();
                t.putString("dim", e.dimension().location().toString());
                t.putLong("time", e.time());
                t.put("set", e.applied().save());
                list.add(t);
            }
            CompoundTag root = new CompoundTag();
            root.put("entries", list);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(root, f);
        } catch (IOException ex) {
            LOG.warn("Renovator: could not write undo history {}", f, ex);
        }
    }
}
