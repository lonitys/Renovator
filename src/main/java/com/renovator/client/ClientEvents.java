package com.renovator.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renovator.Renovator;
import com.renovator.client.projection.ClientProjections;
import com.renovator.client.screen.MaterialReportScreen;
import com.renovator.client.screen.ModeWheelScreen;
import com.renovator.item.WandHooks;
import com.renovator.item.WandMode;
import com.renovator.net.Net;
import com.renovator.net.ProjectionPayloads;
import com.renovator.net.SmallPayloads;
import com.renovator.swap.Templates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client wiring: keys, wand hooks, rendering of selection and previews, input. */
public final class ClientEvents {
    private ClientEvents() {}

    @EventBusSubscriber(modid = Renovator.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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
            Net.clientProjectionSync = ClientProjections::accept;
            Net.clientProjectionRemoved = r -> ClientProjections.removed(r.id());
        }
    }

    @EventBusSubscriber(modid = Renovator.MODID, value = Dist.CLIENT)
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
                if (DetailController.active()) DetailController.confirm();
                else if (InteriorController.active()) InteriorController.confirm();
                else if (PreviewController.active()) PreviewController.confirm();
                else if (PlacementController.active()) PlacementController.confirm();
            }
            while (Keys.TOGGLE_PREVIEW.consumeClick()) {
                if (!wand) continue;
                if (PreviewController.active()) PreviewController.toggleBeforeAfter();
                else if (InteriorController.active()) InteriorController.cycleFurniture();
                else PlacementController.toggleGhost();
            }
            // arrows: details → previous/next and variant; interior → room type, reroll and (↓) furniture; placement → rotate/mirror
            while (Keys.ROTATE_LEFT.consumeClick()) {
                if (!wand) continue;
                if (DetailController.active()) DetailController.next(-1);
                else if (InteriorController.active()) InteriorController.nextType(-1);
                else PlacementController.rotate(-1);
            }
            while (Keys.ROTATE_RIGHT.consumeClick()) {
                if (!wand) continue;
                if (DetailController.active()) DetailController.next(1);
                else if (InteriorController.active()) InteriorController.nextType(1);
                else PlacementController.rotate(1);
            }
            while (Keys.MIRROR.consumeClick()) {
                if (!wand) continue;
                if (DetailController.active()) DetailController.cycleVariant();
                else if (InteriorController.active()) InteriorController.reroll();
                else PlacementController.mirror();
            }
            while (Keys.UNDO.consumeClick()) if (wand) PacketDistributor.sendToServer(new SmallPayloads.UndoRequest());
            // projection keys work with any item in hand
            boolean free = mc.screen == null;
            while (Keys.LAYER_UP.consumeClick()) if (free) ClientProjections.layer(1);
            while (Keys.LAYER_DOWN.consumeClick()) if (free) ClientProjections.layer(-1);
            while (Keys.PROJECTION_VIEW.consumeClick()) if (free && ClientProjections.focusedProjection() != null) ClientProjections.cycleView();
            while (Keys.PROJECTION_STYLE.consumeClick()) if (free && ClientProjections.focusedProjection() != null) ClientProjections.cycleStyle();
            ClientProjections.tick();
            PlacementController.tick();
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            Vec3 cam = e.getCamera().getPosition();
            PreviewController.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            PlacementController.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            DetailController.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            InteriorController.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            ClientProjections.render(e.getModelViewMatrix(), e.getProjectionMatrix(), cam);
            float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
            ClientProjections.renderEntities(e.getPoseStack(), cam, pt);
            PreviewController.renderEntities(e.getPoseStack(), cam, pt);
            PlacementController.renderEntities(e.getPoseStack(), cam, pt);
            DetailController.renderEntities(e.getPoseStack(), cam, pt);
            InteriorController.renderEntities(e.getPoseStack(), cam, pt);
            if (!WandClient.holdingWand() && !PreviewController.active() && !PlacementController.active() && !DetailController.active()) return;
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
            BoundingBox detail = DetailController.currentArea();
            if (detail != null) LevelRenderer.renderLineBox(ps, lines, AABB.of(detail).inflate(0.03), 1f, 0.85f, 0.2f, 1f);
            BoundingBox room = InteriorController.roomBounds();
            if (room != null) LevelRenderer.renderLineBox(ps, lines, AABB.of(room).inflate(0.03), 0.95f, 0.5f, 0.9f, 1f);
            ps.popPose();
            buffers.endBatch(RenderType.lines());
        }

        /**
         * Right-click with a block on a projection ghost builds it there (exact state, via the server); a wrong block
         * says what belongs there. Sneaking keeps vanilla behaviour. Middle-click on a ghost picks the needed block.
         */
        @SubscribeEvent
        public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            if (e.isUseItem() && e.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = mc.player.getMainHandItem();
                if (mc.player.isShiftKeyDown() || !(held.getItem() instanceof BlockItem)) return;
                ClientProjections.Hit hit = ClientProjections.pick(1f);
                if (hit == null) return;
                e.setCanceled(true);
                if (hit.items().contains(held.getItem())) {
                    PacketDistributor.sendToServer(new ProjectionPayloads.Place(hit.projection().id, hit.pos()));
                    e.setSwingHand(true);
                } else {
                    e.setSwingHand(false);
                    ClientState.actionbar(Component.translatable("renovator.projection.wrong", hit.target().getBlock().getName()));
                }
            } else if (e.isPickBlock()) {
                ClientProjections.Hit hit = ClientProjections.pick(1f);
                if (hit == null) return;
                e.setCanceled(true);
                pickItem(mc, hit);
            }
        }

        /** Like vanilla pick block: the needed item into the hand (creative) or from the inventory into the hotbar. */
        private static void pickItem(Minecraft mc, ClientProjections.Hit hit) {
            Item item = hit.target().getBlock().asItem();
            if (!hit.items().contains(item) && !hit.items().isEmpty()) item = hit.items().iterator().next();
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) return;
            Inventory inv = mc.player.getInventory();
            if (mc.player.getAbilities().instabuild) {
                inv.setPickedItem(stack);
                mc.gameMode.handleCreativeModeItemAdd(mc.player.getItemInHand(InteractionHand.MAIN_HAND), 36 + inv.selected);
                return;
            }
            int slot = inv.findSlotMatchingItem(stack);
            if (slot == -1) {
                ClientState.actionbar(Component.translatable("renovator.projection.not_in_inventory", stack.getHoverName()));
            } else if (Inventory.isHotbarSlot(slot)) {
                inv.selected = slot;
            } else {
                mc.gameMode.handlePickItem(slot);
            }
        }

        @SubscribeEvent
        public static void hud(RenderGuiEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.screen != null) return;
            Component line = ClientProjections.hudLine();
            if (line == null) return;
            GuiGraphics g = e.getGuiGraphics();
            int w = mc.font.width(line), x = (g.guiWidth() - w) / 2, y = g.guiHeight() - 84;
            g.fill(x - 3, y - 2, x + w + 3, y + 10, 0x90101820);
            g.drawString(mc.font, line, x, y, 0xFFB8D8FF);
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
            if (!PreviewController.active() && !PlacementController.active() && !DetailController.active() && !InteriorController.active()) return;
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ESCAPE)) return; // focus loss still pauses
            e.setCanceled(true);
            if (DetailController.active()) {
                DetailController.stop();
                ClientState.actionbar(net.minecraft.network.chat.Component.translatable("renovator.detail.stopped"));
            } else if (InteriorController.active()) {
                InteriorController.stop();
                ClientState.actionbar(net.minecraft.network.chat.Component.translatable("renovator.interior.cancelled"));
            } else if (PreviewController.active()) {
                PreviewController.cancel();
                ClientState.actionbar(net.minecraft.network.chat.Component.translatable("renovator.preview.cancelled"));
            } else {
                PlacementController.stop();
                ClientState.actionbar(net.minecraft.network.chat.Component.translatable("renovator.place.cancelled"));
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
            PreviewController.cancel();
            PlacementController.stop();
            DetailController.stop();
            InteriorController.stop();
            ClientState.resetAll();
            ClientProjections.clear();
            Templates.setDatapack(java.util.List.of());
        }
    }
}
