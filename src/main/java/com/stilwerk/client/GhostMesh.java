package com.stilwerk.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
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

    private GhostMesh(@Nullable VertexBuffer buffer, int count) {
        this.buffer = buffer;
        this.count = count;
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
        BlockColors colors = mc.getBlockColors();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        VertexConsumer consumer = new AlphaConsumer(builder, alpha);
        PoseStack ps = new PoseStack();
        RandomSource random = RandomSource.create(42);
        int n = 0;
        for (Block b : blocks) {
            if (n >= max) break;
            BlockState state = b.state();
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
            BakedModel model = dispatcher.getBlockModel(state);
            BlockPos world = anchor.offset(b.rel());
            int tint = colors.getColor(state, level, world, 0);
            float r = 1, g = 1, bl = 1;
            if (tint != -1) {
                r = (tint >> 16 & 255) / 255f;
                g = (tint >> 8 & 255) / 255f;
                bl = (tint & 255) / 255f;
            }
            ps.pushPose();
            ps.translate(b.rel().getX() + 0.5, b.rel().getY() + 0.5, b.rel().getZ() + 0.5);
            ps.scale(1.004f, 1.004f, 1.004f); // slightly larger than the real block: no z-fighting
            ps.translate(-0.5, -0.5, -0.5);
            random.setSeed(42);
            for (RenderType rt : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                dispatcher.getModelRenderer().renderModel(ps.last(), consumer, state, model, r, g, bl,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
            }
            ps.popPose();
            n++;
        }
        MeshData mesh = builder.build();
        if (mesh == null) return new GhostMesh(null, 0);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return new GhostMesh(vb, n);
    }

    /** Draws the mesh with its rel (0,0,0) at {@code anchor}. */
    public void draw(Matrix4f modelView, Matrix4f projection, Vec3 camera, BlockPos anchor) {
        if (buffer == null) return;
        Matrix4f mv = new Matrix4f(modelView).translate(
                (float) (anchor.getX() - camera.x), (float) (anchor.getY() - camera.y), (float) (anchor.getZ() - camera.z));
        RenderType rt = RenderType.translucent();
        rt.setupRenderState();
        RenderSystem.depthMask(false);
        buffer.bind();
        buffer.drawWithShader(mv, projection, GameRenderer.getRendertypeTranslucentShader());
        VertexBuffer.unbind();
        RenderSystem.depthMask(true);
        rt.clearRenderState();
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
