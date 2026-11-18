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

/** Translucent "ghost" blocks tessellated once into a GPU buffer and drawn every frame at an anchor. */
public final class GhostMesh implements AutoCloseable {
    public record Block(BlockPos rel, BlockState state) {}

    private final @Nullable VertexBuffer buffer;
    private final int count;
    /** Blocks drawn by a block-entity renderer (beds, chests, signs ...): rendered live with their renderer. */
    private final List<EntityGhost> entities;

    private record EntityGhost(BlockPos rel, BlockEntity be) {}

    private GhostMesh(@Nullable VertexBuffer buffer, int count, List<EntityGhost> entities) {
        this.buffer = buffer;
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
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        VertexConsumer consumer = new AlphaConsumer(builder, alpha);
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
        MeshData mesh = builder.build();
        if (mesh == null) return new GhostMesh(null, 0, entities);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return new GhostMesh(vb, n, entities);
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
        if (buffer != null) buffer.close();
    }

    /** Multiplies the alpha of every vertex. */
    private record AlphaConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, (int) (a * alpha));
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
}
