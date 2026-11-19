package com.renovator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Translucent "ghost" blocks tessellated once into a GPU buffer and drawn every frame at an anchor. Optionally tinted,
 * and optionally pulsing: then the vertices are uploaded once per size step (each block scaled about its own centre)
 * and the draw call picks the step for the current time - no custom shader, so shader packs keep working.
 */
public final class GhostMesh implements AutoCloseable {
    public record Block(BlockPos rel, BlockState state) {}

    /** Smallest size of a pulsing ghost (fraction of a block); it breathes between this and 1. */
    public static final float PULSE_MIN = 0.85f;
    public static final int PULSE_STEPS = 6;
    private static final long PULSE_MILLIS = 1200;
    /** Above this many vertices a ghost does not pulse (memory for the extra buffers). */
    private static final int PULSE_MAX_VERTICES = 400_000;

    private final @Nullable VertexBuffer[] buffers;
    private final int count;
    /** Blocks drawn by a block-entity renderer (beds, chests, signs ...): rendered live with their renderer. */
    private final List<EntityGhost> entities;

    private record EntityGhost(BlockPos rel, BlockEntity be) {}

    private GhostMesh(@Nullable VertexBuffer[] buffers, int count, List<EntityGhost> entities) {
        this.buffers = buffers;
        this.count = count;
        this.entities = entities;
    }

    public int count() {
        return count;
    }

    /**
     * @param anchor world position of rel (0,0,0), used for biome tints
     */
    public static GhostMesh build(ClientLevel level, BlockPos anchor, List<Block> blocks, float alpha, int max) {
        return build(level, anchor, blocks, alpha, max, 1f, 1f, 1f, 0f, false);
    }

    /**
     * @param tr tint colour, red part (tg, tb: green, blue)
     * @param blend 0: multiply by the tint; above: mix that much of the tint colour in (keeps it readable on dark wood)
     * @param pulse breathe between {@link #PULSE_MIN} and full size
     */
    public static GhostMesh build(ClientLevel level, BlockPos anchor, List<Block> blocks, float alpha, int max, float tr, float tg, float tb,
                                  float blend, boolean pulse) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        BufferBuilder builder = pulse ? null : Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        CaptureConsumer capture = pulse ? new CaptureConsumer() : null;
        VertexConsumer consumer = new TintConsumer(pulse ? capture : builder, alpha, tr, tg, tb, blend);
        PoseStack ps = new PoseStack();
        RandomSource random = RandomSource.create(42);
        Long2ObjectOpenHashMap<BlockState> ghosts = new Long2ObjectOpenHashMap<>(Math.min(blocks.size(), max));
        for (int i = 0; i < blocks.size() && i < max; i++) {
            Block b = blocks.get(i);
            ghosts.put(anchor.offset(b.rel()).asLong(), b.state());
        }
        GhostView view = new GhostView(level, ghosts);
        List<EntityGhost> entities = new java.util.ArrayList<>();
        int n = 0;
        for (Block b : blocks) {
            if (n >= max) break;
            BlockState state = b.state();
            if (state.getBlock() instanceof EntityBlock eb && entities.size() < 256) {
                BlockEntity be = eb.newBlockEntity(anchor.offset(b.rel()), state);
                if (be != null && mc.getBlockEntityRenderDispatcher().getRenderer(be) != null) {
                    be.setLevel(level);
                    entities.add(new EntityGhost(b.rel(), be));
                }
            }
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
            BakedModel model = dispatcher.getBlockModel(state);
            BlockPos world = anchor.offset(b.rel());
            long seed = state.getSeed(world);
            ps.pushPose();
            ps.translate(b.rel().getX(), b.rel().getY(), b.rel().getZ());
            if (capture != null) capture.centre(b.rel().getX() + 0.5f, b.rel().getY() + 0.5f, b.rel().getZ() + 0.5f);
            random.setSeed(seed);
            // tesselated like a chunk against "the world after the change": real light, shading and tints,
            // faces between ghost blocks culled
            for (RenderType rt : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                dispatcher.getModelRenderer().tesselateBlock(view, model, state, world, ps, consumer, true, random, seed,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
            }
            ps.popPose();
            n++;
        }
        if (capture != null) {
            if (capture.size() == 0) return new GhostMesh(null, 0, entities);
            int steps = capture.size() > PULSE_MAX_VERTICES ? 1 : PULSE_STEPS;
            VertexBuffer[] vbs = new VertexBuffer[steps];
            for (int i = 0; i < steps; i++) {
                float scale = steps == 1 ? 1f : PULSE_MIN + (1f - PULSE_MIN) * i / (steps - 1);
                vbs[i] = upload(capture.write(scale));
            }
            return new GhostMesh(vbs, n, entities);
        }
        MeshData mesh = builder.build();
        if (mesh == null) return new GhostMesh(null, 0, entities);
        return new GhostMesh(new VertexBuffer[]{upload(mesh)}, n, entities);
    }

    private static @Nullable VertexBuffer upload(@Nullable MeshData mesh) {
        if (mesh == null) return null;
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return vb;
    }

    /** Draws the block-entity ghosts (opaque, full bright) with their renderers. */
    public void drawEntities(PoseStack pose, Vec3 camera, BlockPos anchor, float partialTick) {
        if (entities.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (EntityGhost g : entities) {
            BlockEntityRenderer<BlockEntity> r = mc.getBlockEntityRenderDispatcher().getRenderer(g.be());
            if (r == null) continue;
            pose.pushPose();
            pose.translate(anchor.getX() + g.rel().getX() - camera.x, anchor.getY() + g.rel().getY() - camera.y,
                    anchor.getZ() + g.rel().getZ() - camera.z);
            try {
                r.render(g.be(), partialTick, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } catch (RuntimeException e) {
                // a renderer that needs a real world entry: skip it in the preview
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Draws the mesh with its rel (0,0,0) at {@code anchor}. */
    public void draw(Matrix4f modelView, Matrix4f projection, Vec3 camera, BlockPos anchor) {
        if (buffers == null) return;
        VertexBuffer buffer = buffers[0];
        if (buffers.length > 1) {
            // up and down: 0, 1, ... steps-1, ..., 1
            double phase = (System.currentTimeMillis() % PULSE_MILLIS) / (double) PULSE_MILLIS;
            double wave = 0.5 - 0.5 * Math.cos(phase * 2 * Math.PI);
            buffer = buffers[(int) Math.round(wave * (buffers.length - 1))];
        }
        if (buffer == null) return;
        Matrix4f mv = new Matrix4f(modelView).translate(
                (float) (anchor.getX() - camera.x), (float) (anchor.getY() - camera.y), (float) (anchor.getZ() - camera.z));
        RenderType rt = RenderType.translucent();
        rt.setupRenderState();
        // pull the ghost towards the camera: it wins against the real block in the same place, even on inner faces
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -10.0f);
        buffer.bind();
        buffer.drawWithShader(mv, projection, GameRenderer.getRendertypeTranslucentShader());
        VertexBuffer.unbind();
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        rt.clearRenderState();
    }

    public boolean hasEntities() {
        return !entities.isEmpty();
    }

    @Override
    public void close() {
        if (buffers == null) return;
        for (VertexBuffer b : buffers) if (b != null) b.close();
    }

    /** Tints every vertex (multiply, or mix in a colour) and multiplies its alpha. */
    private record TintConsumer(VertexConsumer delegate, float alpha, float tr, float tg, float tb, float blend) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (blend > 0) {
                delegate.setColor((int) (r * (1 - blend) + 255 * tr * blend), (int) (g * (1 - blend) + 255 * tg * blend),
                        (int) (b * (1 - blend) + 255 * tb * blend), (int) (a * alpha));
            } else {
                delegate.setColor((int) (r * tr), (int) (g * tg), (int) (b * tb), (int) (a * alpha));
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    /** Records the tessellated vertices with the centre of their block, to write them out at several sizes. */
    private static final class CaptureConsumer implements VertexConsumer {
        private float[] pos = new float[3 * 4096], uv = new float[2 * 4096], normal = new float[3 * 4096], centre = new float[3 * 4096];
        private int[] colour = new int[4096], light = new int[4096];
        private int n = -1;
        private float cx, cy, cz;

        void centre(float x, float y, float z) {
            cx = x;
            cy = y;
            cz = z;
        }

        int size() {
            return n + 1;
        }

        private void grow() {
            int cap = colour.length * 2;
            pos = java.util.Arrays.copyOf(pos, 3 * cap);
            uv = java.util.Arrays.copyOf(uv, 2 * cap);
            normal = java.util.Arrays.copyOf(normal, 3 * cap);
            centre = java.util.Arrays.copyOf(centre, 3 * cap);
            colour = java.util.Arrays.copyOf(colour, cap);
            light = java.util.Arrays.copyOf(light, cap);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            n++;
            if (n >= colour.length) grow();
            pos[3 * n] = x;
            pos[3 * n + 1] = y;
            pos[3 * n + 2] = z;
            centre[3 * n] = cx;
            centre[3 * n + 1] = cy;
            centre[3 * n + 2] = cz;
            colour[n] = 0xFFFFFFFF;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            colour[n] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            uv[2 * n] = u;
            uv[2 * n + 1] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            light[n] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            normal[3 * n] = x;
            normal[3 * n + 1] = y;
            normal[3 * n + 2] = z;
            return this;
        }

        /** All vertices with every block scaled about its centre. */
        @Nullable MeshData write(float scale) {
            BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int i = 0; i <= n; i++) {
                float x = centre[3 * i] + (pos[3 * i] - centre[3 * i]) * scale;
                float y = centre[3 * i + 1] + (pos[3 * i + 1] - centre[3 * i + 1]) * scale;
                float z = centre[3 * i + 2] + (pos[3 * i + 2] - centre[3 * i + 2]) * scale;
                int c = colour[i];
                out.addVertex(x, y, z).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).setUv(uv[2 * i], uv[2 * i + 1])
                        .setUv2(light[i] & 0xFFFF, light[i] >>> 16).setNormal(normal[3 * i], normal[3 * i + 1], normal[3 * i + 2]);
            }
            return out.build();
        }
    }
}
