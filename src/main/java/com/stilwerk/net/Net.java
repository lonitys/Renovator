package com.stilwerk.net;

import com.stilwerk.Stilwerk;
import com.stilwerk.net.SmallPayloads.BlueprintChunk;
import com.stilwerk.net.SmallPayloads.ConfirmRequest;
import com.stilwerk.net.SmallPayloads.MaterialReport;
import com.stilwerk.net.SmallPayloads.TemplatesSync;
import com.stilwerk.net.SmallPayloads.UndoRequest;
import com.stilwerk.swap.Templates;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration. Client-bound handlers are set by the client setup (no client classes here). */
public final class Net {
    private Net() {}

    public static Consumer<MaterialReport> clientReportHandler = r -> {};

    @EventBusSubscriber(modid = Stilwerk.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent e) {
            PayloadRegistrar r = e.registrar("1");
            r.playToServer(SwapRequest.TYPE, SwapRequest.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ServerOps.swap((ServerPlayer) ctx.player(), p)));
            r.playToServer(PlaceRequest.TYPE, PlaceRequest.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ServerOps.place((ServerPlayer) ctx.player(), p)));
            r.playToServer(DetailRequest.TYPE, DetailRequest.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ServerOps.detail((ServerPlayer) ctx.player(), p)));
            r.playToServer(BlueprintChunk.TYPE, BlueprintChunk.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> UploadBuffer.accept((ServerPlayer) ctx.player(), p)));
            r.playToServer(ConfirmRequest.TYPE, ConfirmRequest.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ServerOps.confirm((ServerPlayer) ctx.player(), p)));
            r.playToServer(UndoRequest.TYPE, UndoRequest.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ServerOps.undo((ServerPlayer) ctx.player())));
            r.playToClient(MaterialReport.TYPE, MaterialReport.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> clientReportHandler.accept(p)));
            r.playToClient(TemplatesSync.TYPE, TemplatesSync.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> Templates.setDatapack(p.templates())));
        }
    }

    @EventBusSubscriber(modid = Stilwerk.MODID)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void datapackSync(OnDatapackSyncEvent e) {
            TemplatesSync sync = new TemplatesSync(Templates.datapack());
            e.getRelevantPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, sync));
        }

        @SubscribeEvent
        public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
            ServerOps.forget(e.getEntity().getUUID());
        }
    }
}
