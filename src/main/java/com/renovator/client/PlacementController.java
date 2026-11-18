package com.renovator.client;

import com.renovator.blueprint.Blueprint;
import com.renovator.blueprint.BlueprintIO;
import com.renovator.blueprint.BlueprintPlanner;
import com.renovator.blueprint.Transform;
import com.renovator.config.ClientConfig;
import com.renovator.mix.MixPalette;
import com.renovator.net.PlaceRequest;
import com.renovator.net.SmallPayloads.BlueprintChunk;
import com.renovator.swap.SwapMapping;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Placing a blueprint: the ghost follows the crosshair; arrows rotate/mirror, Ctrl+wheel moves it up/down. */
public final class PlacementController {
    private PlacementController() {}

    private static @Nullable Blueprint original;
    private static Rotation rotation = Rotation.NONE;
    private static Mirror mirror = Mirror.NONE;
    private static int height;
    private static SwapMapping mapping = SwapMapping.EMPTY;
    private static Map<String, MixPalette> mixes = Map.of();
    private static long seed;
    public static boolean overwrite;

    private static @Nullable Blueprint transformed;
    private static @Nullable GhostMesh mesh;
    private static @Nullable BlockPos meshOrigin;
    private static BlockPos origin = BlockPos.ZERO;
    private static boolean showGhost = true;
    private static int ticksSinceStyle;

    public static boolean active() {
        return original != null;
    }

    public static @Nullable Blueprint blueprint() {
        return original;
    }

    public static @Nullable Blueprint transformed() {
        return transformed;
    }

    public static SwapMapping mapping() {
        return mapping;
    }

    public static Map<String, MixPalette> mixes() {
        return mixes;
    }

    public static void start(Blueprint bp) {
        PreviewController.cancel();
        stop();
        original = bp;
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        height = 0;
        mapping = SwapMapping.EMPTY;
        mixes = Map.of();
        seed = ThreadLocalRandom.current().nextLong();
        showGhost = true;
        ClientState.actionbar(Component.translatable("renovator.place.hint", bp.meta().name()));
    }

    public static void stop() {
        original = null;
        transformed = null;
        if (mesh != null) mesh.close();
        mesh = null;
        meshOrigin = null;
    }

    public static void setMapping(SwapMapping m, Map<String, MixPalette> mx) {
        mapping = m;
        mixes = mx;
        invalidate();
    }

    public static void rotate(int dir) {
        if (!active()) return;
        rotation = rotation.getRotated(dir > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
        transformed = null;
        invalidate();
    }

    public static void mirror() {
        if (!active()) return;
        mirror = switch (mirror) {
            case NONE -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.NONE;
        };
        transformed = null;
        invalidate();
    }

    public static void height(int d) {
        height += d;
    }

    public static void toggleGhost() {
        showGhost = !showGhost;
    }

    private static void invalidate() {
        if (mesh != null) mesh.close();
        mesh = null;
        meshOrigin = null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (original == null || mc.level == null || mc.player == null) return;
        if (transformed == null) transformed = Transform.apply(original, rotation, mirror);
        Vec3i size = transformed.size();
        // look further than the reach so the ghost lands on the ground the player looks at
        HitResult hit = mc.player.pick(64.0, 1.0f, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos base = bh.getBlockPos().relative(bh.getDirection());
            origin = base.offset(-size.getX() / 2, height, -size.getZ() / 2);
        } else if (meshOrigin == null) {
            BlockPos base = BlockPos.containing(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(12)));
            origin = base.offset(-size.getX() / 2, height, -size.getZ() / 2);
        }
        ticksSinceStyle++;
        boolean moved = meshOrigin == null || !meshOrigin.equals(origin);
        // a mix looks different at every position: restyle while moving, at most every 4 ticks
        if (mesh == null || (moved && !mapping.mixIds().isEmpty() && ticksSinceStyle >= 4)) rebuild(mc);
    }

    private static void rebuild(Minecraft mc) {
        ticksSinceStyle = 0;
        if (mesh != null) mesh.close();
        Blueprint styled = BlueprintPlanner.style(transformed, origin, mapping, mixes, seed).blueprint();
        List<GhostMesh.Block> blocks = new ArrayList<>();
        styled.forEach((p, s, be) -> {
            if (!s.isAir()) blocks.add(new GhostMesh.Block(p.immutable(), s));
        });
        mesh = GhostMesh.build(mc.level, origin, blocks, ClientConfig.ghostAlpha(), ClientConfig.maxGhostBlocks());
        meshOrigin = origin;
    }

    public static @Nullable BoundingBox bounds() {
        if (transformed == null) return null;
        Vec3i s = transformed.size();
        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + s.getX() - 1, origin.getY() + s.getY() - 1, origin.getZ() + s.getZ() - 1);
    }

    public static void renderEntities(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, float partialTick) {
        if (mesh != null && showGhost) mesh.drawEntities(pose, camera, origin, partialTick);
    }

    public static void render(Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        if (mesh != null && showGhost) mesh.draw(modelView, projection, camera, origin);
    }

    /** Uploads the blueprint (in ≤ 30 000-byte chunks), asks the server to place it here, and ends placing. */
    public static void confirm() {
        if (original == null) return;
        byte[] data = BlueprintIO.toNetwork(original);
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        int uploadId = ThreadLocalRandom.current().nextInt();
        int total = Math.max(1, (data.length + BlueprintChunk.MAX_CHUNK - 1) / BlueprintChunk.MAX_CHUNK);
        for (int i = 0; i < total; i++) {
            byte[] part = Arrays.copyOfRange(data, i * BlueprintChunk.MAX_CHUNK, Math.min(data.length, (i + 1) * BlueprintChunk.MAX_CHUNK));
            PacketDistributor.sendToServer(new BlueprintChunk(uploadId, i, total, part));
        }
        PacketDistributor.sendToServer(new PlaceRequest(ClientState.nextRequestId(), hash, origin, rotation, mirror, mapping,
                PreviewController.usedMixes(mapping, mixes), seed, overwrite, ClientState.buildMode()));
        String name = original.meta().name();
        stop();
        ClientState.actionbar(Component.translatable("renovator.place.placed", name));
    }
}
