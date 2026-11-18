package com.renovator.projection;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/** Per-player projections, kept in {@code <world>/renovator/projections/<uuid>.nbt}; at most {@link #MAX}, oldest dropped. */
public final class ProjectionStore {
    private ProjectionStore() {}

    public static final int MAX = 8;
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<UUID, List<Projection>>> CACHE = new WeakHashMap<>();

    /** Adds a projection; returns the ones dropped to stay within {@link #MAX}. */
    public static synchronized List<Projection> add(MinecraftServer server, UUID player, Projection p) {
        List<Projection> l = list0(server, player);
        l.add(p);
        List<Projection> dropped = new ArrayList<>();
        while (l.size() > MAX) dropped.add(l.remove(0));
        write(server, player, l);
        return dropped;
    }

    public static synchronized List<Projection> list(MinecraftServer server, UUID player) {
        return List.copyOf(list0(server, player));
    }

    public static synchronized Optional<Projection> get(MinecraftServer server, UUID player, UUID id) {
        for (Projection p : list0(server, player)) if (p.id().equals(id)) return Optional.of(p);
        return Optional.empty();
    }

    public static synchronized boolean remove(MinecraftServer server, UUID player, UUID id) {
        List<Projection> l = list0(server, player);
        boolean removed = l.removeIf(p -> p.id().equals(id));
        if (removed) write(server, player, l);
        return removed;
    }

    /** Tests: forget the in-memory cache so the next access reads the file. */
    public static synchronized void clearCacheForTests(MinecraftServer server) {
        CACHE.remove(server);
    }

    static Path file(MinecraftServer server, UUID player) {
        return server.getWorldPath(LevelResource.ROOT).resolve("renovator").resolve("projections").resolve(player + ".nbt");
    }

    private static List<Projection> list0(MinecraftServer server, UUID player) {
        return CACHE.computeIfAbsent(server, s -> new HashMap<>()).computeIfAbsent(player, p -> read(server, p));
    }

    private static List<Projection> read(MinecraftServer server, UUID player) {
        List<Projection> l = new ArrayList<>();
        Path f = file(server, player);
        if (!Files.isRegularFile(f)) return l;
        try {
            ListTag list = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()).getList("projections", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) l.add(Projection.load(list.getCompound(i)));
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Renovator: could not read projections {}", f, ex);
        }
        return l;
    }

    private static void write(MinecraftServer server, UUID player, List<Projection> l) {
        Path f = file(server, player);
        try {
            if (l.isEmpty()) {
                Files.deleteIfExists(f);
                return;
            }
            ListTag list = new ListTag();
            for (Projection p : l) list.add(p.save());
            CompoundTag root = new CompoundTag();
            root.put("projections", list);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(root, f);
        } catch (IOException ex) {
            LOG.warn("Renovator: could not write projections {}", f, ex);
        }
    }
}
