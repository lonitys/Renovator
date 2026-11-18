package com.stilwerk.client;

import com.stilwerk.config.ClientConfig;
import com.stilwerk.core.Change;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.net.SwapRequest;
import com.stilwerk.swap.SwapMapping;
import com.stilwerk.swap.SwapPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** The style-swap preview: plans on the client world, shows ghosts, sends the request on confirm. */
public final class PreviewController {
    private PreviewController() {}

    private static @Nullable GhostMesh mesh;
    private static BlockPos anchor = BlockPos.ZERO;
    private static boolean showAfter = true;
    private static @Nullable Selection selection;
    private static SwapMapping mapping = SwapMapping.EMPTY;
    private static Map<String, MixPalette> mixes = Map.of();
    private static long seed;
    private static boolean includeNature;
    private static List<SwapPlanner.Kept> kept = List.of();
    private static int changes;

    public static boolean active() {
        return mesh != null;
    }

    public static List<SwapPlanner.Kept> lastKept() {
        return kept;
    }

    public static int lastChanges() {
        return changes;
    }

    /** Plans the swap on the client world; returns the plan (also used by the swap screen for its notes). */
    public static @Nullable SwapPlanner.Result plan(Selection sel, SwapMapping m, Map<String, MixPalette> mx, long sd, boolean nature) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        ResolvedSelection res = sel.resolve(mc.level, ClientState.CLIENT_LIMIT);
        if (res.truncated()) {
            ClientState.actionbar(Component.translatable("stilwerk.select.too_big"));
            return null;
        }
        return SwapPlanner.plan(mc.level, res, m, mx, sd, new SwapPlanner.Options(nature, !mc.player.isCreative()));
    }

    public static void showSwap(Selection sel, SwapMapping m, Map<String, MixPalette> mx, long sd, boolean nature) {
        SwapPlanner.Result r = plan(sel, m, mx, sd, nature);
        if (r == null) return;
        cancel();
        PlacementController.stop();
        selection = sel;
        mapping = m;
        mixes = mx;
        seed = sd;
        includeNature = nature;
        kept = r.kept();
        changes = r.changes().size();
        Minecraft mc = Minecraft.getInstance();
        List<Change> list = r.changes().changes();
        anchor = list.isEmpty() ? BlockPos.ZERO : list.get(0).pos();
        List<GhostMesh.Block> blocks = new ArrayList<>(list.size());
        for (Change c : list) blocks.add(new GhostMesh.Block(c.pos().subtract(anchor), c.newState()));
        // nearly opaque: the preview should look like the result, not like a blend with the old blocks
        mesh = GhostMesh.build(mc.level, anchor, blocks, Math.max(0.9f, ClientConfig.ghostAlpha()), ClientConfig.maxGhostBlocks());
        showAfter = true;
        ClientState.actionbar(Component.translatable("stilwerk.preview.hint", changes));
    }

    public static void toggleBeforeAfter() {
        showAfter = !showAfter;
        ClientState.actionbar(Component.translatable(showAfter ? "stilwerk.preview.after" : "stilwerk.preview.before"));
    }

    public static void confirm() {
        if (!active() || selection == null) return;
        PacketDistributor.sendToServer(new SwapRequest(ClientState.nextRequestId(), selection, mapping, usedMixes(mapping, mixes),
                seed, includeNature, false));
        cancel();
    }

    /** Sends the swap without a preview (swap screen "Apply"). */
    public static void applyDirect(Selection sel, SwapMapping m, Map<String, MixPalette> mx, long sd, boolean nature) {
        cancel();
        PacketDistributor.sendToServer(new SwapRequest(ClientState.nextRequestId(), sel, m, usedMixes(m, mx), sd, nature, false));
    }

    public static List<MixPalette> usedMixes(SwapMapping m, Map<String, MixPalette> mx) {
        List<MixPalette> out = new ArrayList<>();
        for (String id : m.mixIds()) {
            MixPalette p = mx.get(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public static void cancel() {
        if (mesh != null) mesh.close();
        mesh = null;
    }

    public static void render(Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        if (mesh != null && showAfter) mesh.draw(modelView, projection, camera, anchor);
    }
}
