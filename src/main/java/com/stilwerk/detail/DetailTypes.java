package com.stilwerk.detail;

import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Value types of the detail assistant. */
public final class DetailTypes {
    private DetailTypes() {}

    /** One way to realise a suggestion. {@code key} is a translation key. */
    public record Variant(String key, ChangeSet changes) {}

    /** A spot that could look better, with its variants (never empty). */
    public record Suggestion(String rule, BlockPos anchor, BoundingBox area, List<Variant> variants) {
        public String key() {
            return rule + "@" + anchor.asLong();
        }

        public String titleKey() {
            return "stilwerk.detail." + rule;
        }
    }

    /** The player's pick: which suggestion (rule + anchor) with which variant. */
    public record Choice(String rule, BlockPos anchor, int variant) {
        public static final StreamCodec<ByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Choice::rule, BlockPos.STREAM_CODEC, Choice::anchor, ByteBufCodecs.VAR_INT, Choice::variant, Choice::new);

        public String key() {
            return rule + "@" + anchor.asLong();
        }
    }

    /** Builds a suggestion from non-empty variants; null when none is left. */
    public static Suggestion suggestion(String rule, BlockPos anchor, List<Variant> variants) {
        List<Variant> ok = new ArrayList<>();
        for (Variant v : variants) if (!v.changes().isEmpty()) ok.add(v);
        if (ok.isEmpty()) return null;
        int minX = anchor.getX(), minY = anchor.getY(), minZ = anchor.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        for (Variant v : ok)
            for (Change c : v.changes().changes()) {
                BlockPos p = c.pos();
                minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
            }
        return new Suggestion(rule, anchor.immutable(), new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), List.copyOf(ok));
    }
}
