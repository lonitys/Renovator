package com.stilwerk.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stilwerk.Stilwerk;
import com.stilwerk.client.screen.MaterialReportScreen;
import com.stilwerk.client.screen.ModeWheelScreen;
import com.stilwerk.item.WandHooks;
import com.stilwerk.item.WandMode;
import com.stilwerk.net.Net;
import com.stilwerk.net.SmallPayloads;
import com.stilwerk.swap.Templates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client wiring: keys, wand hooks, rendering of selection and previews, input. */
public final class ClientEvents {
    private ClientEvents() {}

    @EventBusSubscriber(modid = Stilwerk.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            for (var k : Keys.ALL) e.register(k);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            WandHooks.clientUseOn = WandClient::useOn;
            WandHooks.clientUse = WandClient::use;
            Net.clientReportHandler = r -> Minecraft.getInstance().setScreen(new MaterialReportScreen(r));
        }
    }

    @EventBusSubscriber(modid = Stilwerk.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            boolean wand = WandClient.holdingWand() && mc.screen == null;
            while (Keys.MODE_WHEEL.consumeClick()) if (wand) mc.setScreen(new ModeWheelScreen());
            while (Keys.CONFIRM.consumeClick()) {
                if (!wand) continue;
                if (PreviewController.active()) PreviewController.confirm();
                else if (PlacementController.active()) PlacementController.confirm();
            }
            while (Keys.TOGGLE_PREVIEW.consumeClick()) {
                if (!wand) continue;
                if (PreviewController.active()) PreviewController.toggleBeforeAfter();
                else PlacementController.toggleGhost();
            }
            while (Keys.ROTATE_LEFT.consumeClick()) if (wand) PlacementController.rotate(-1);
            while (Keys.ROTATE_RIGHT.consumeClick()) if (wand) PlacementController.rotate(1);
            while (Keys.MIRROR.consumeClick()) if (wand) PlacementController.mirror();
            while (Keys.UNDO.consumeClick()) if (wand) PacketDistributor.sendToServer(new SmallPayloads.UndoRequest());
            PlacementController.tick();
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            Vec3 cam = e.getCamera().getPosition();
            PreviewController.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            PlacementController.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            if (!WandClient.holdingWand() && !PreviewController.active() && !PlacementController.active()) return;
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            BoundingBox sel = ClientState.bounds();
            if (sel != null) {
                boolean flood = ClientState.floodStart != null;
                LevelRenderer.renderLineBox(ps, lines, AABB.of(sel).inflate(0.01), flood ? 1f : 0.2f, flood ? 0.75f : 0.85f, flood ? 0.2f : 1f, 1f);
            } else if (ClientState.cornerA != null) {
                LevelRenderer.renderLineBox(ps, lines, new AABB(ClientState.cornerA).inflate(0.01), 0.2f, 0.85f, 1f, 1f);
            }
            BoundingBox place = PlacementController.active() ? PlacementController.bounds() : null;
            if (place != null) LevelRenderer.renderLineBox(ps, lines, AABB.of(place).inflate(0.02), 0.3f, 1f, 0.4f, 1f);
            ps.popPose();
            buffers.endBatch(RenderType.lines());
        }

        @SubscribeEvent
        public static void scroll(InputEvent.MouseScrollingEvent e) {
            if (!WandClient.holdingWand() || !Screen.hasControlDown() || Minecraft.getInstance().screen != null) return;
            int d = e.getScrollDeltaY() > 0 ? 1 : -1;
            if (PlacementController.active()) {
                PlacementController.height(d);
            } else if (ClientState.mode == WandMode.SELECT && ClientState.cornerA != null && ClientState.cornerB != null) {
                Direction face = Direction.orderedByNearest(Minecraft.getInstance().player)[0];
                ClientState.setBox(pushFace(BoundingBox.fromCorners(ClientState.cornerA, ClientState.cornerB), face, d));
            } else {
                return;
            }
            e.setCanceled(true);
        }

        /** Moves one face of the box along its normal: +1 grows, -1 shrinks (never below one block). */
        static BoundingBox pushFace(BoundingBox b, Direction face, int d) {
            int minX = b.minX(), minY = b.minY(), minZ = b.minZ(), maxX = b.maxX(), maxY = b.maxY(), maxZ = b.maxZ();
            switch (face) {
                case EAST -> maxX = Math.max(minX, maxX + d);
                case WEST -> minX = Math.min(maxX, minX - d);
                case UP -> maxY = Math.max(minY, maxY + d);
                case DOWN -> minY = Math.min(maxY, minY - d);
                case SOUTH -> maxZ = Math.max(minZ, maxZ + d);
                case NORTH -> minZ = Math.min(maxZ, minZ - d);
            }
            return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @SubscribeEvent
        public static void screenOpening(ScreenEvent.Opening e) {
            if (!(e.getNewScreen() instanceof PauseScreen)) return;
            if (!PreviewController.active() && !PlacementController.active()) return;
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ESCAPE)) return; // focus loss still pauses
            e.setCanceled(true);
            if (PreviewController.active()) {
                PreviewController.cancel();
                ClientState.actionbar(net.minecraft.network.chat.Component.translatable("stilwerk.preview.cancelled"));
            } else {
                PlacementController.stop();
                ClientState.actionbar(net.minecraft.network.chat.Component.translatable("stilwerk.place.cancelled"));
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
            PreviewController.cancel();
            PlacementController.stop();
            ClientState.resetAll();
            Templates.setDatapack(java.util.List.of());
        }
    }
}
