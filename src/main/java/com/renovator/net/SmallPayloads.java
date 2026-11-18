package com.renovator.net;

import com.renovator.Renovator;
import com.renovator.swap.Template;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/** The small payloads: upload chunks, confirm, undo, material report, template sync. */
public final class SmallPayloads {
    private SmallPayloads() {}

    /** Client → server: one piece (≤ 30 000 bytes) of a blueprint in network form. */
    public record BlueprintChunk(int uploadId, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final int MAX_CHUNK = 30_000;
        public static final Type<BlueprintChunk> TYPE = new Type<>(Renovator.id("blueprint_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintChunk> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BlueprintChunk::uploadId,
                ByteBufCodecs.VAR_INT, BlueprintChunk::index,
                ByteBufCodecs.VAR_INT, BlueprintChunk::total,
                ByteBufCodecs.byteArray(MAX_CHUNK), BlueprintChunk::data,
                BlueprintChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The answer to the build dialog. */
    public enum ConfirmAction {
        CANCEL, BUILD, PROJECT;

        public static final StreamCodec<io.netty.buffer.ByteBuf, ConfirmAction> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal);
    }

    /** Client → server: answer to the build dialog (build as far as affordable, keep as projection, or cancel). */
    public record ConfirmRequest(int id, ConfirmAction action) implements CustomPacketPayload {
        public static final Type<ConfirmRequest> TYPE = new Type<>(Renovator.id("confirm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ConfirmRequest::id, ConfirmAction.STREAM_CODEC, ConfirmRequest::action, ConfirmRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: undo the last operation. */
    public record UndoRequest() implements CustomPacketPayload {
        public static final Type<UndoRequest> TYPE = new Type<>(Renovator.id("undo"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UndoRequest> STREAM_CODEC = StreamCodec.unit(new UndoRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One line of a material report. */
    public record Line(Item item, int need, int have, int missing, boolean emc) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Line::item,
                ByteBufCodecs.VAR_INT, Line::need,
                ByteBufCodecs.VAR_INT, Line::have,
                ByteBufCodecs.VAR_INT, Line::missing,
                ByteBufCodecs.BOOL, Line::emc,
                Line::new);
    }

    /** Server → client: survival materials are missing; the player decides. */
    public record MaterialReport(int id, List<Line> lines, boolean complete) implements CustomPacketPayload {
        public static final Type<MaterialReport> TYPE = new Type<>(Renovator.id("material_report"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MaterialReport> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MaterialReport::id,
                Line.STREAM_CODEC.apply(ByteBufCodecs.list(4096)), MaterialReport::lines,
                ByteBufCodecs.BOOL, MaterialReport::complete,
                MaterialReport::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: the server's datapack templates. */
    public record TemplatesSync(List<Template> templates) implements CustomPacketPayload {
        public static final Type<TemplatesSync> TYPE = new Type<>(Renovator.id("templates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TemplatesSync> STREAM_CODEC = StreamCodec.composite(
                Template.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), TemplatesSync::templates, TemplatesSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
