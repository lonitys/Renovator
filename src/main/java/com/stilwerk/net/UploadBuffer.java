package com.stilwerk.net;

import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintIO;
import com.stilwerk.config.ServerConfig;
import com.stilwerk.net.SmallPayloads.BlueprintChunk;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Assembles uploaded blueprint chunks per player and keeps the last few complete blueprints by hash. */
public final class UploadBuffer {
    private UploadBuffer() {}

    /** Cells (including VOID) an uploaded blueprint may span. */
    public static final long MAX_VOLUME = 64_000_000L;
    private static final int KEEP = 2;

    private static final class Partial {
        final int uploadId, total;
        final byte[][] chunks;
        int received, bytes;

        Partial(int uploadId, int total) {
            this.uploadId = uploadId;
            this.total = total;
            this.chunks = new byte[total][];
        }
    }

    private static final Map<UUID, Partial> PARTIAL = new HashMap<>();
    private static final Map<UUID, LinkedHashMap<String, Blueprint>> DONE = new HashMap<>();

    public static synchronized void accept(ServerPlayer p, BlueprintChunk c) {
        int maxBytes = ServerConfig.maxUploadBytes();
        int maxChunks = maxBytes / BlueprintChunk.MAX_CHUNK + 1;
        if (c.total() <= 0 || c.total() > maxChunks || c.index() < 0 || c.index() >= c.total()) {
            fail(p, "too large");
            return;
        }
        Partial part = PARTIAL.get(p.getUUID());
        if (part == null || part.uploadId != c.uploadId() || part.total != c.total()) {
            part = new Partial(c.uploadId(), c.total());
            PARTIAL.put(p.getUUID(), part);
        }
        if (part.chunks[c.index()] == null) {
            part.chunks[c.index()] = c.data();
            part.received++;
            part.bytes += c.data().length;
        }
        if (part.bytes > maxBytes) {
            PARTIAL.remove(p.getUUID());
            fail(p, "too large");
            return;
        }
        if (part.received < part.total) return;
        PARTIAL.remove(p.getUUID());
        ByteArrayOutputStream all = new ByteArrayOutputStream(part.bytes);
        for (byte[] b : part.chunks) all.writeBytes(b);
        byte[] data = all.toByteArray();
        try {
            Blueprint bp = BlueprintIO.fromNetwork(data, BuiltInRegistries.BLOCK.asLookup(), MAX_VOLUME);
            LinkedHashMap<String, Blueprint> done = DONE.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
            done.put(hex(sha1(data)), bp);
            while (done.size() > KEEP) done.remove(done.keySet().iterator().next());
        } catch (IOException | RuntimeException e) {
            fail(p, e.getMessage());
        }
    }

    public static synchronized @Nullable Blueprint get(ServerPlayer p, byte[] hash) {
        Map<String, Blueprint> done = DONE.get(p.getUUID());
        return done == null ? null : done.get(hex(hash));
    }

    public static synchronized void forget(UUID player) {
        PARTIAL.remove(player);
        DONE.remove(player);
    }

    private static void fail(ServerPlayer p, String why) {
        p.sendSystemMessage(Component.translatable("stilwerk.error.upload", String.valueOf(why)));
    }

    static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
