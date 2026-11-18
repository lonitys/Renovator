package com.renovator.client;

import com.renovator.config.ClientConfig;
import com.renovator.core.ResolvedSelection;
import com.renovator.core.Selection;
import com.renovator.item.WandMode;
import com.renovator.swap.SwapMapping;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** The client's selection, mode and swap settings (per session). */
public final class ClientState {
    private ClientState() {}

    /** Largest selection the client resolves for previews and censuses. */
    /** Client-side resolving has no block limit (the server applies its own, unlimited by default). */
    public static final int CLIENT_LIMIT = Integer.MAX_VALUE;

    public static WandMode mode = WandMode.SELECT;
    public static @Nullable BlockPos cornerA, cornerB;
    public static boolean nextIsB;
    public static @Nullable BlockPos floodStart;
    public static @Nullable ResolvedSelection floodResolved;

    /** Mapping being edited in the swap screen (kept between openings). */
    public static SwapMapping mapping = SwapMapping.EMPTY;
    public static boolean includeNature;
    public static long seed = ThreadLocalRandom.current().nextLong();

    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);

    public static int nextRequestId() {
        return REQUEST_IDS.getAndIncrement();
    }

    public static @Nullable Selection selection() {
        if (floodStart != null) return new Selection.Flood(floodStart, ClientConfig.floodRadius());
        if (cornerA != null && cornerB != null) return new Selection.Box(cornerA, cornerB);
        return null;
    }

    /** Bounds to draw: the box, or the resolved flood. */
    public static @Nullable BoundingBox bounds() {
        if (floodStart != null && floodResolved != null && !floodResolved.isEmpty()) return floodResolved.bounds();
        if (cornerA != null && cornerB != null) return BoundingBox.fromCorners(cornerA, cornerB);
        return null;
    }

    public static void setCorner(BlockPos pos) {
        floodStart = null;
        floodResolved = null;
        if (!nextIsB || cornerA == null) {
            cornerA = pos.immutable();
            cornerB = null;
            nextIsB = true;
            actionbar(Component.translatable("renovator.select.corner_a", pos.getX(), pos.getY(), pos.getZ()));
        } else {
            cornerB = pos.immutable();
            nextIsB = false;
            announce();
        }
    }

    /** Shift held while confirming: keep it as a projection instead of building. */
    public static com.renovator.net.BuildMode buildMode() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown() ? com.renovator.net.BuildMode.PROJECTION : com.renovator.net.BuildMode.ASK;
    }

    public static void setFlood(BlockPos start) {
        cornerA = cornerB = null;
        nextIsB = false;
        Minecraft mc = Minecraft.getInstance();
        ResolvedSelection r = new Selection.Flood(start, ClientConfig.floodRadius()).resolve(mc.level, CLIENT_LIMIT);
        if (r.isEmpty()) {
            floodStart = null;
            floodResolved = null;
            actionbar(Component.translatable(r.truncated() ? "renovator.select.too_big" : "renovator.select.nothing_built"));
            return;
        }
        floodStart = start.immutable();
        floodResolved = r;
        announce();
    }

    public static void setBox(BoundingBox bb) {
        cornerA = new BlockPos(bb.minX(), bb.minY(), bb.minZ());
        cornerB = new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ());
        floodStart = null;
        floodResolved = null;
        announce();
    }

    public static void clear() {
        cornerA = cornerB = floodStart = null;
        floodResolved = null;
        nextIsB = false;
        actionbar(Component.translatable("renovator.select.cleared"));
    }

    public static void resetAll() {
        cornerA = cornerB = floodStart = null;
        floodResolved = null;
        nextIsB = false;
        mapping = SwapMapping.EMPTY;
        includeNature = false;
        mode = WandMode.SELECT;
    }

    private static void announce() {
        BoundingBox bb = bounds();
        if (bb == null) return;
        int count = floodResolved != null ? floodResolved.size() : bb.getXSpan() * bb.getYSpan() * bb.getZSpan();
        actionbar(Component.translatable("renovator.select.info", bb.getXSpan(), bb.getYSpan(), bb.getZSpan(), count));
    }

    public static void actionbar(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, true);
    }
}
