package com.renovator.client.projection;

import com.renovator.client.ClientState;
import com.renovator.client.GhostMesh;
import com.renovator.config.ClientConfig;
import com.renovator.net.ProjectionPayloads;
import com.renovator.net.ProjectionPayloads.Action;
import com.renovator.net.ProjectionPayloads.ProjAction;
import com.renovator.projection.CellState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The player's projections on the client: assembled from sync parts, checked against the world a few thousand cells
 * per tick, drawn layer by layer, and the focused one navigated with layers, view mode and style.
 */
public final class ClientProjections {
    private ClientProjections() {}

    public enum View { ALL, SINGLE, UP_TO }
    public enum Style { BLUE, REAL }

    private static final int SCAN_PER_TICK = 5_000;
    private static final int REBUILDS_PER_FRAME = 2;
    /** Tint colours mixed into the ghosts (blend {@link #MIX}). */
    private static final float[] BLUE = {0.25f, 0.55f, 1.0f}, RED = {1.0f, 0.2f, 0.2f};
    private static final float MIX = 0.75f;

    private static final Map<UUID, ClientProjection> ALL = new LinkedHashMap<>();
    private static final Map<UUID, List<ProjectionPayloads.Sync>> PARTS = new HashMap<>();
    private static final Set<UUID> HIDDEN = new HashSet<>();
    private static @Nullable UUID focused;
    private static int layerY;
    private static boolean wasDoneLayer;

    // ---------------------------------------------------------------- sync

    public static void accept(ProjectionPayloads.Sync s) {
        List<ProjectionPayloads.Sync> parts = PARTS.computeIfAbsent(s.id(), k -> new ArrayList<>());
        if (s.part() == 0) parts.clear();
        parts.add(s);
        if (parts.size() < s.parts()) return;
        PARTS.remove(s.id());
        int n = 0;
        for (ProjectionPayloads.Sync p : parts) n += p.positions().length;
        long[] pos = new long[n];
        BlockState[] target = new BlockState[n], old = new BlockState[n];
        int[] group = new int[n];
        int k = 0;
        for (ProjectionPayloads.Sync p : parts)
            for (int i = 0; i < p.positions().length; i++, k++) {
                pos[k] = p.positions()[i];
                target[k] = Block.stateById(p.targets()[i]);
                old[k] = Block.stateById(p.olds()[i]);
                group[k] = p.groups()[i];
            }
        ClientProjection prev = ALL.remove(s.id());
        if (prev != null) prev.closeMeshes();
        ClientProjection cp = new ClientProjection(s.id(), s.labelKey(), s.labelArg(), s.dimension(), pos, target, old, group);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) cp.scan(level, cp.size());
        ALL.put(cp.id, cp);
        focus(cp.id);
    }

    public static void removed(UUID id) {
        ClientProjection cp = ALL.remove(id);
        if (cp != null) cp.closeMeshes();
        HIDDEN.remove(id);
        if (id.equals(focused)) {
            focused = null;
            for (ClientProjection p : here()) focus(p.id);
        }
    }

    public static void clear() {
        for (ClientProjection cp : ALL.values()) cp.closeMeshes();
        ALL.clear();
        PARTS.clear();
        HIDDEN.clear();
        focused = null;
    }

    // ---------------------------------------------------------------- state

    /** Projections in the player's current dimension. */
    public static List<ClientProjection> here() {
        ClientLevel level = Minecraft.getInstance().level;
        List<ClientProjection> out = new ArrayList<>();
        if (level == null) return out;
        for (ClientProjection cp : ALL.values()) if (cp.dimension.equals(level.dimension().location())) out.add(cp);
        return out;
    }

    public static List<ClientProjection> all() {
        return List.copyOf(ALL.values());
    }

    public static @Nullable ClientProjection focusedProjection() {
        return focused == null ? null : ALL.get(focused);
    }

    public static void focus(UUID id) {
        ClientProjection cp = ALL.get(id);
        if (cp == null) return;
        focused = id;
        HIDDEN.remove(id);
        layerY = cp.firstOpenLayer();
        wasDoneLayer = false;
        dirtyAll(cp);
        refreshHud();
    }

    public static boolean hidden(UUID id) {
        return HIDDEN.contains(id);
    }

    public static void toggleHidden(UUID id) {
        if (!HIDDEN.remove(id)) HIDDEN.add(id);
    }

    public static View view() {
        return switch (ClientConfig.projectionView()) {
            case "single" -> View.SINGLE;
            case "up_to" -> View.UP_TO;
            default -> View.ALL;
        };
    }

    public static Style style() {
        return "real".equals(ClientConfig.projectionStyle()) ? Style.REAL : Style.BLUE;
    }

    public static int layerY() {
        return layerY;
    }

    /** Index (1-based) of the current layer and the number of layers of the focused projection. */
    public static int[] layerIndex() {
        ClientProjection cp = focusedProjection();
        if (cp == null || cp.layers.length == 0) return new int[]{0, 0};
        int i = java.util.Arrays.binarySearch(cp.layers, layerY);
        return new int[]{i < 0 ? 1 : i + 1, cp.layers.length};
    }

    public static void cycleView() {
        View next = View.values()[(view().ordinal() + 1) % View.values().length];
        ClientConfig.setProjectionView(switch (next) {
            case ALL -> "all";
            case SINGLE -> "single";
            case UP_TO -> "up_to";
        });
        ClientState.actionbar(Component.translatable("renovator.projection.view." + next.name().toLowerCase(java.util.Locale.ROOT)));
        refreshHud();
    }

    public static void cycleStyle() {
        Style next = style() == Style.BLUE ? Style.REAL : Style.BLUE;
        ClientConfig.setProjectionStyle(next == Style.REAL ? "real" : "blue");
        for (ClientProjection cp : ALL.values()) dirtyAll(cp);
        ClientState.actionbar(Component.translatable("renovator.projection.style." + next.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** One layer up or down in the focused projection (switches to the single-layer view when showing all). */
    public static void layer(int d) {
        ClientProjection cp = focusedProjection();
        if (cp == null || cp.layers.length == 0) return;
        if (view() == View.ALL) ClientConfig.setProjectionView("single");
        int i = java.util.Arrays.binarySearch(cp.layers, layerY);
        if (i < 0) i = -i - 1;
        i = Math.max(0, Math.min(cp.layers.length - 1, i + d));
        layerY = cp.layers[i];
        wasDoneLayer = false;
        int[] li = layerIndex();
        ClientState.actionbar(Component.translatable("renovator.projection.layer", li[0], li[1], layerY));
        refreshHud();
    }

    private static void dirtyAll(ClientProjection cp) {
        for (ClientProjection.LayerMesh m : cp.meshes.values()) m.dirty = true;
    }

    /** Is a layer of this projection shown? Only the focused one is filtered by the view mode. */
    static boolean layerVisible(ClientProjection cp, int y) {
        if (HIDDEN.contains(cp.id)) return false;
        if (!cp.id.equals(focused)) return true;
        return switch (view()) {
            case ALL -> true;
            case SINGLE -> y == layerY;
            case UP_TO -> y <= layerY;
        };
    }

    // ---------------------------------------------------------------- tick

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (++hudAge >= 10) {
            hudAge = 0;
            hud = computeHud();
        }
        List<ClientProjection> here = here();
        if (here.isEmpty()) return;
        int budget = Math.max(500, SCAN_PER_TICK / here.size());
        for (ClientProjection cp : here) {
            cp.scan(mc.level, budget);
            if (cp.finished() && !cp.removeRequested) {
                cp.removeRequested = true;
                PacketDistributor.sendToServer(new ProjAction(cp.id, Action.REMOVE_IF_DONE));
            }
        }
        // a finished layer in the layer views: go on with the next open one
        ClientProjection f = focusedProjection();
        if (f != null && view() != View.ALL && !f.finished()) {
            boolean doneLayer = f.openIn(layerY) == 0;
            if (doneLayer && !wasDoneLayer) {
                int next = f.firstOpenLayer();
                if (next != layerY) {
                    layerY = next;
                    int[] li = layerIndex();
                    ClientState.actionbar(Component.translatable("renovator.projection.layer_done", li[0], li[1], layerY));
                    doneLayer = false;
                }
            }
            wasDoneLayer = doneLayer;
        }
    }

    // ---------------------------------------------------------------- render

    public static void render(Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int rebuilds = 0;
        boolean real = style() == Style.REAL;
        List<ClientProjection> here = here();
        for (ClientProjection cp : here) {
            for (int y : cp.layers) {
                if (!layerVisible(cp, y)) continue;
                ClientProjection.LayerMesh m = cp.meshes.computeIfAbsent(y, k -> new ClientProjection.LayerMesh());
                if (m.dirty && rebuilds < REBUILDS_PER_FRAME) {
                    rebuild(mc.level, cp, y, m, real);
                    rebuilds++;
                }
            }
        }
        // blocked (red) first: translucent ghosts in front must not hide them
        for (ClientProjection cp : here)
            for (int y : cp.layers) {
                ClientProjection.LayerMesh m = cp.meshes.get(y);
                if (m != null && m.blocked != null && layerVisible(cp, y)) m.blocked.draw(modelView, projection, camera, cp.anchor);
            }
        for (ClientProjection cp : here)
            for (int y : cp.layers) {
                ClientProjection.LayerMesh m = cp.meshes.get(y);
                if (m != null && m.open != null && layerVisible(cp, y)) m.open.draw(modelView, projection, camera, cp.anchor);
            }
    }

    public static void renderEntities(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, float partialTick) {
        for (ClientProjection cp : here()) {
            for (int y : cp.layers) {
                if (!layerVisible(cp, y)) continue;
                ClientProjection.LayerMesh m = cp.meshes.get(y);
                if (m != null && m.open != null) m.open.drawEntities(pose, camera, cp.anchor, partialTick);
            }
        }
    }

    private static void rebuild(ClientLevel level, ClientProjection cp, int y, ClientProjection.LayerMesh m, boolean real) {
        m.close();
        m.dirty = false;
        IntArrayList cells = cp.byLayer.get(y);
        List<GhostMesh.Block> open = new ArrayList<>(), blocked = new ArrayList<>();
        for (int k = 0; k < cells.size(); k++) {
            int i = cells.getInt(k);
            CellState s = cp.cellState(i);
            if (s == CellState.DONE) continue;
            GhostMesh.Block b = new GhostMesh.Block(cp.pos(i).subtract(cp.anchor), cp.target[i]);
            (s == CellState.BLOCKED ? blocked : open).add(b);
        }
        int max = ClientConfig.maxGhostBlocks();
        if (!open.isEmpty()) {
            m.open = real ? GhostMesh.build(level, cp.anchor, open, 0.4f, max, 1f, 1f, 1f, 0f, true)
                    : GhostMesh.build(level, cp.anchor, open, 0.6f, max, BLUE[0], BLUE[1], BLUE[2], MIX, false);
        }
        if (!blocked.isEmpty()) m.blocked = GhostMesh.build(level, cp.anchor, blocked, 0.6f, max, RED[0], RED[1], RED[2], MIX, false);
    }

    // ---------------------------------------------------------------- picking

    /** A ghost cell under the crosshair. */
    public record Hit(ClientProjection projection, int cell) {
        public BlockPos pos() {
            return projection.pos(cell);
        }

        public BlockState target() {
            return projection.target[cell];
        }

        public Set<Item> items() {
            return projection.groupItems(cell);
        }
    }

    /**
     * Walks the view ray block by block up to the interaction range: the first visible open ghost wins; a real block
     * on the way (other than the old block a swap ghost stands in) stops the ray.
     */
    public static @Nullable Hit pick(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        List<ClientProjection> here = here();
        if (here.isEmpty()) return null;
        Vec3 from = mc.player.getEyePosition(partialTick);
        Vec3 dir = mc.player.getViewVector(partialTick);
        double reach = mc.player.blockInteractionRange();
        int x = net.minecraft.util.Mth.floor(from.x), y = net.minecraft.util.Mth.floor(from.y), z = net.minecraft.util.Mth.floor(from.z);
        int sx = (int) Math.signum(dir.x), sy = (int) Math.signum(dir.y), sz = (int) Math.signum(dir.z);
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.x), tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.y),
                tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.z);
        double tx = sx == 0 ? Double.MAX_VALUE : ((sx > 0 ? Math.floor(from.x) + 1 - from.x : from.x - Math.floor(from.x)) * tdx);
        double ty = sy == 0 ? Double.MAX_VALUE : ((sy > 0 ? Math.floor(from.y) + 1 - from.y : from.y - Math.floor(from.y)) * tdy);
        double tz = sz == 0 ? Double.MAX_VALUE : ((sz > 0 ? Math.floor(from.z) + 1 - from.z : from.z - Math.floor(from.z)) * tdz);
        double t = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (t <= reach) {
            m.set(x, y, z);
            long key = m.asLong();
            for (ClientProjection cp : here) {
                int i = cp.index.get(key);
                if (i >= 0 && cp.cellState(i) == CellState.OPEN && layerVisible(cp, y)) return new Hit(cp, i);
            }
            BlockState s = mc.level.getBlockState(m);
            if (t > 0 && !s.isAir() && !s.canBeReplaced()) return null;
            if (tx <= ty && tx <= tz) {
                t = tx;
                tx += tdx;
                x += sx;
            } else if (ty <= tz) {
                t = ty;
                ty += tdy;
                y += sy;
            } else {
                t = tz;
                tz += tdz;
                z += sz;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- HUD

    private static @Nullable Component hud;
    private static int hudAge = 99;

    /** "Blueprint: house · layer 3/12 (y 64) · 45/120 built · missing: 12× Oak Planks, 4× Glass", or null (cached). */
    public static @Nullable Component hudLine() {
        return hud;
    }

    static void refreshHud() {
        hudAge = 99;
    }

    private static @Nullable Component computeHud() {
        Minecraft mc = Minecraft.getInstance();
        ClientProjection cp = focusedProjection();
        if (cp == null || mc.player == null || mc.level == null || HIDDEN.contains(cp.id)) return null;
        if (!cp.dimension.equals(mc.level.dimension().location())) return null;
        Component line;
        if (view() == View.ALL) {
            line = Component.translatable("renovator.projection.hud_all", cp.label(), cp.done(), cp.size());
        } else {
            int[] li = layerIndex();
            line = Component.translatable("renovator.projection.hud", cp.label(), li[0], li[1], layerY, cp.done(), cp.size());
        }
        Object2IntOpenHashMap<Item> need = cp.need(view() == View.ALL ? null : layerY);
        if (!mc.player.isCreative() && !need.isEmpty()) {
            record Missing(Item item, int count) {}
            List<Missing> missing = new ArrayList<>();
            for (Object2IntMap.Entry<Item> e : need.object2IntEntrySet()) {
                int have = mc.player.getInventory().countItem(e.getKey());
                if (have < e.getIntValue()) missing.add(new Missing(e.getKey(), e.getIntValue() - have));
            }
            if (!missing.isEmpty()) {
                missing.sort((a, b) -> Integer.compare(b.count(), a.count()));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, missing.size()); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(missing.get(i).count()).append("× ").append(new ItemStack(missing.get(i).item()).getHoverName().getString());
                }
                if (missing.size() > 3) sb.append(" …");
                line = Component.empty().append(line).append(Component.translatable("renovator.projection.hud_missing", sb.toString()));
            }
        }
        return line;
    }
}
