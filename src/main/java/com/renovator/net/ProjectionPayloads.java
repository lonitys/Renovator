package com.renovator.net;

import com.renovator.Renovator;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Network messages of the build projection. */
public final class ProjectionPayloads {
    private ProjectionPayloads() {}

    /** Cells per {@link Sync} part (keeps each message well below the 1 MiB payload limit). */
    public static final int PART = 30_000;

    /**
     * Server → client: one part of a projection. Per cell: position, target and expected old state (block state ids)
     * and the index of its group. Parts arrive in order; the client keeps the projection once all are there.
     */
    public record Sync(UUID id, String labelKey, String labelArg, ResourceLocation dimension, int part, int parts,
                       long[] positions, int[] targets, int[] olds, int[] groups) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Renovator.id("projection_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, s.id);
                    buf.writeUtf(s.labelKey);
                    buf.writeUtf(s.labelArg);
                    buf.writeResourceLocation(s.dimension);
                    buf.writeVarInt(s.part);
                    buf.writeVarInt(s.parts);
                    buf.writeLongArray(s.positions);
                    buf.writeVarIntArray(s.targets);
                    buf.writeVarIntArray(s.olds);
                    buf.writeVarIntArray(s.groups);
                },
                buf -> new Sync(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readResourceLocation(), buf.readVarInt(),
                        buf.readVarInt(), buf.readLongArray(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: the projection is gone (finished, deleted or dropped). */
    public record Removed(UUID id) implements CustomPacketPayload {
        public static final Type<Removed> TYPE = new Type<>(Renovator.id("projection_removed"));
        public static final StreamCodec<ByteBuf, Removed> STREAM_CODEC = UUIDUtil.STREAM_CODEC.map(Removed::new, Removed::id);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: build the group of this projection cell by hand (the player holds a matching block). */
    public record Place(UUID id, BlockPos pos) implements CustomPacketPayload {
        public static final Type<Place> TYPE = new Type<>(Renovator.id("projection_place"));
        public static final StreamCodec<ByteBuf, Place> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Place::id, BlockPos.STREAM_CODEC, Place::pos, Place::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum Scope {
        /** The groups starting in layer y. */
        LAYER,
        /** The groups starting at or below layer y. */
        UP_TO,
        ALL;

        public static final StreamCodec<ByteBuf, Scope> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal);
    }

    /** Client → server: build the open part of a projection automatically (inventory, then EMC in survival). */
    public record Build(UUID id, Scope scope, int y) implements CustomPacketPayload {
        public static final Type<Build> TYPE = new Type<>(Renovator.id("projection_build"));
        public static final StreamCodec<ByteBuf, Build> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Build::id, Scope.STREAM_CODEC, Build::scope, ByteBufCodecs.VAR_INT, Build::y, Build::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum Action {
        DELETE,
        /** Sent when the client sees everything built; the server checks and removes it. */
        REMOVE_IF_DONE;

        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal);
    }

    public record ProjAction(UUID id, Action action) implements CustomPacketPayload {
        public static final Type<ProjAction> TYPE = new Type<>(Renovator.id("projection_action"));
        public static final StreamCodec<ByteBuf, ProjAction> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ProjAction::id, Action.STREAM_CODEC, ProjAction::action, ProjAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
