package com.stilwerk.client;

import com.stilwerk.client.screen.InteriorScreen;
import com.stilwerk.config.ClientConfig;
import com.stilwerk.core.Change;
import com.stilwerk.family.Family;
import com.stilwerk.interior.FurnitureChoice;
import com.stilwerk.interior.InteriorGenerator;
import com.stilwerk.interior.ModuleLibrary;
import com.stilwerk.interior.Room;
import com.stilwerk.interior.RoomDetector;
import com.stilwerk.interior.RoomType;
import com.stilwerk.net.InteriorRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Interior preview: room detected on the client, furnished with a type and seed; ↑ reroll, ←/→ type, ↓ furniture, Enter, Esc. */
public final class InteriorController {
    private InteriorController() {}

    private static @Nullable Room room;
    private static BlockPos clicked = BlockPos.ZERO;
    private static Direction face = Direction.UP;
    private static Family wood;
    private static int typeIndex;
    private static long seed;
    private static boolean previewing;
    private static @Nullable GhostMesh mesh;
    private static BlockPos anchor = BlockPos.ZERO;

    public static boolean active() {
        return previewing;
    }

    public static @Nullable Room room() {
        return room;
    }

    /** Right-click in a room: detect it and show the room type menu. */
    public static void open(BlockPos pos, Direction side) {
        Minecraft mc = Minecraft.getInstance();
        RoomDetector.Result r = RoomDetector.detect(mc.level, pos, side);
        if (r.room() == null) {
            BlockPos at = r.at();
            ClientState.actionbar(at == null ? Component.translatable(r.error())
                    : Component.translatable(r.error() + "_at", at.getX(), at.getY(), at.getZ()));
            return;
        }
        stop();
        room = r.room();
        clicked = pos.immutable();
        face = side;
        wood = InteriorGenerator.houseWood(mc.level, room);
        mc.setScreen(new InteriorScreen(room));
    }

    /** Starts the preview with a room type ("auto" picks one from the room). */
    public static void start(String typeId) {
        if (room == null) return;
        Minecraft mc = Minecraft.getInstance();
        PreviewController.cancel();
        PlacementController.stop();
        DetailController.stop();
        RoomType t = typeId.equals("auto") ? InteriorGenerator.auto(mc.level, room) : ModuleLibrary.type(typeId);
        List<RoomType> types = ModuleLibrary.types();
        typeIndex = Math.max(0, types.indexOf(t));
        seed = ThreadLocalRandom.current().nextLong();
        previewing = true;
        regenerate();
    }

    public static void reroll() {
        if (!previewing) return;
        seed = ThreadLocalRandom.current().nextLong();
        regenerate();
    }

    /** The furniture choice in use (remembered in the client config). */
    public static String furniture() {
        return FurnitureChoice.normalize(ClientConfig.interiorFurniture());
    }

    /** Next furniture choice (all mods, one mod, vanilla only); refreshes a running preview. */
    public static void cycleFurniture() {
        ClientConfig.setInteriorFurniture(FurnitureChoice.next(furniture()));
        if (previewing) regenerate();
    }

    public static void nextType(int d) {
        if (!previewing) return;
        typeIndex = Math.floorMod(typeIndex + d, ModuleLibrary.types().size());
        regenerate();
    }

    private static RoomType type() {
        return ModuleLibrary.types().get(typeIndex);
    }

    private static void regenerate() {
        Minecraft mc = Minecraft.getInstance();
        if (mesh != null) mesh.close();
        mesh = null;
        InteriorGenerator.Result r = InteriorGenerator.generate(mc.level, room, type(), seed, wood, furniture());
        List<Change> changes = r.changes().changes();
        anchor = room.start();
        List<GhostMesh.Block> blocks = new ArrayList<>(changes.size());
        for (Change c : changes) blocks.add(new GhostMesh.Block(c.pos().subtract(anchor), c.newState()));
        mesh = GhostMesh.build(mc.level, anchor, blocks, Math.max(0.8f, ClientConfig.ghostAlpha()), ClientConfig.maxGhostBlocks());
        long groups = r.placed().stream().filter(id -> !id.equals("rug") && !id.equals("light")).count();
        ClientState.actionbar(Component.translatable("stilwerk.interior.hint", Component.translatable(type().translationKey()), groups,
                FurnitureChoice.label(furniture())));
    }

    public static void confirm() {
        if (!previewing) return;
        PacketDistributor.sendToServer(new InteriorRequest(ClientState.nextRequestId(), clicked, face, type().id(), seed, furniture(), false));
        ClientState.actionbar(Component.translatable("stilwerk.interior.placed", Component.translatable(type().translationKey())));
        stop();
    }

    public static void stop() {
        previewing = false;
        if (mesh != null) mesh.close();
        mesh = null;
    }

    public static @Nullable BoundingBox roomBounds() {
        return previewing && room != null ? room.bounds() : null;
    }

    public static void renderEntities(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, float partialTick) {
        if (mesh != null && previewing) mesh.drawEntities(pose, camera, anchor, partialTick);
    }

    public static void render(Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        if (mesh != null && previewing) mesh.draw(modelView, projection, camera, anchor);
    }
}
