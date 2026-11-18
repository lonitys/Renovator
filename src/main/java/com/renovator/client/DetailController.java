package com.renovator.client;

import com.renovator.config.ClientConfig;
import com.renovator.core.Change;
import com.renovator.core.ResolvedSelection;
import com.renovator.core.Selection;
import com.renovator.detail.DetailPlanner;
import com.renovator.detail.DetailTypes.Choice;
import com.renovator.detail.DetailTypes.Suggestion;
import com.renovator.detail.DetailTypes.Variant;
import com.renovator.net.DetailRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Going through detail suggestions in the world: ghost of the current variant, ←/→, ↑, Enter, Esc. */
public final class DetailController {
    private DetailController() {}

    private static List<Suggestion> list = new ArrayList<>();
    private static int index, variant;
    private static @Nullable GhostMesh mesh;
    private static BlockPos meshAnchor = BlockPos.ZERO;
    private static Optional<Selection> selection = Optional.empty();
    private static Optional<BlockPos> clicked = Optional.empty();
    private static long seed;

    public static boolean active() {
        return !list.isEmpty();
    }

    /** Suggestions for the selection (computed on the client world), or null when the selection is too big. */
    public static @Nullable List<Suggestion> findForSelection(Selection sel) {
        Minecraft mc = Minecraft.getInstance();
        ResolvedSelection res = sel.resolve(mc.level, ClientState.CLIENT_LIMIT);
        if (res.truncated()) {
            ClientState.actionbar(Component.translatable("renovator.select.too_big"));
            return null;
        }
        return DetailPlanner.find(mc.level, res, ClientState.seed);
    }

    /** Walks through suggestions found in the selection, starting at {@code start}. */
    public static void walk(Selection sel, List<Suggestion> found, int start) {
        begin(found, start, Optional.of(sel), Optional.empty());
    }

    /** Suggestions around a clicked block. */
    public static void targeted(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        List<Suggestion> found = DetailPlanner.near(mc.level, pos, ClientState.seed);
        if (found.isEmpty()) {
            ClientState.actionbar(Component.translatable("renovator.detail.none_here"));
            return;
        }
        begin(found, 0, Optional.empty(), Optional.of(pos.immutable()));
    }

    private static void begin(List<Suggestion> found, int start, Optional<Selection> sel, Optional<BlockPos> click) {
        PreviewController.cancel();
        PlacementController.stop();
        InteriorController.stop();
        list = new ArrayList<>(found);
        selection = sel;
        clicked = click;
        seed = ClientState.seed;
        index = Math.max(0, Math.min(start, list.size() - 1));
        variant = 0;
        rebuild();
    }

    public static void next(int d) {
        if (!active()) return;
        index = Math.floorMod(index + d, list.size());
        variant = 0;
        rebuild();
    }

    public static void cycleVariant() {
        if (!active()) return;
        variant = (variant + 1) % list.get(index).variants().size();
        rebuild();
    }

    /** Applies the shown variant and moves on to the next suggestion. */
    public static void confirm() {
        if (!active()) return;
        Suggestion s = list.get(index);
        send(List.of(new Choice(s.rule(), s.anchor(), variant)));
        list.remove(index);
        if (list.isEmpty()) {
            stop();
            ClientState.actionbar(Component.translatable("renovator.detail.all_done"));
            return;
        }
        index = index % list.size();
        variant = 0;
        rebuild();
    }

    /** Applies the first variant of every suggestion. */
    public static void applyAll(Selection sel, List<Suggestion> found) {
        List<Choice> choices = new ArrayList<>();
        for (Suggestion s : found) choices.add(new Choice(s.rule(), s.anchor(), 0));
        selection = Optional.of(sel);
        clicked = Optional.empty();
        seed = ClientState.seed;
        send(choices);
        stop();
    }

    private static void send(List<Choice> choices) {
        PacketDistributor.sendToServer(new DetailRequest(ClientState.nextRequestId(), selection, clicked, seed, choices, false));
    }

    public static void stop() {
        list = new ArrayList<>();
        if (mesh != null) mesh.close();
        mesh = null;
    }

    private static void rebuild() {
        if (mesh != null) mesh.close();
        mesh = null;
        Suggestion s = list.get(index);
        Variant v = s.variants().get(variant);
        List<Change> changes = v.changes().changes();
        meshAnchor = changes.isEmpty() ? s.anchor() : changes.get(0).pos();
        List<GhostMesh.Block> blocks = new ArrayList<>();
        for (Change c : changes) blocks.add(new GhostMesh.Block(c.pos().subtract(meshAnchor), c.newState()));
        Minecraft mc = Minecraft.getInstance();
        mesh = GhostMesh.build(mc.level, meshAnchor, blocks, Math.max(0.85f, ClientConfig.ghostAlpha()), ClientConfig.maxGhostBlocks());
        ClientState.actionbar(Component.translatable("renovator.detail.hint", Component.translatable(s.titleKey()), index + 1, list.size(),
                variant + 1, s.variants().size(), variantName(v)));
    }

    public static Component variantName(Variant v) {
        String key = v.key();
        int bar = key.indexOf('|');
        if (bar < 0) return Component.translatable(key);
        String arg = key.substring(bar + 1);
        Component name = arg.startsWith("renovator.") ? Component.translatable(arg) : Component.literal(arg);
        return Component.translatable(key.substring(0, bar), name);
    }

    public static @Nullable BoundingBox currentArea() {
        return active() ? list.get(index).area() : null;
    }

    public static void renderEntities(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, float partialTick) {
        if (mesh != null) mesh.drawEntities(pose, camera, meshAnchor, partialTick);
    }

    public static void render(Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        if (mesh != null) mesh.draw(modelView, projection, camera, meshAnchor);
    }
}
