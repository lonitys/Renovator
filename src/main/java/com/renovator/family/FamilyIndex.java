package com.renovator.family;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jetbrains.annotations.Nullable;

/**
 * All block families, built deterministically from the block registry plus datapack overrides.
 * Priority: overrides, wood types, shaped blocks by class + name, pillars, singletons.
 */
public final class FamilyIndex {
    public record Member(Family family, Shape shape) {}

    /** A family definition from a datapack. */
    public record Override(ResourceLocation family, boolean wood, Map<Shape, ResourceLocation> blocks) {}

    private static volatile FamilyIndex current;
    private static volatile List<Override> overrides = List.of();

    private final Map<Block, Member> members;
    private final Map<ResourceLocation, Family> families;

    private FamilyIndex(Map<Block, Member> members, Map<ResourceLocation, Family> families) {
        this.members = members;
        this.families = families;
    }

    public static FamilyIndex get() {
        FamilyIndex i = current;
        if (i == null) {
            synchronized (FamilyIndex.class) {
                if (current == null) current = build(overrides);
                i = current;
            }
        }
        return i;
    }

    /** Rebuilds with new datapack overrides (server reload) or none (client login). */
    public static synchronized void rebuild(List<Override> newOverrides) {
        overrides = List.copyOf(newOverrides);
        current = build(overrides);
    }

    public @Nullable Member lookup(Block b) {
        return members.get(b);
    }

    public @Nullable Family byId(ResourceLocation id) {
        return families.get(id);
    }

    /** All families sorted by id. */
    public Collection<Family> all() {
        return families.values();
    }

    // ---------------------------------------------------------------- building

    private static final Map<Shape, String> SUFFIX = new LinkedHashMap<>();

    static {
        SUFFIX.put(Shape.WALL_HANGING_SIGN, "_wall_hanging_sign");
        SUFFIX.put(Shape.HANGING_SIGN, "_hanging_sign");
        SUFFIX.put(Shape.WALL_SIGN, "_wall_sign");
        SUFFIX.put(Shape.SIGN, "_sign");
        SUFFIX.put(Shape.FENCE_GATE, "_fence_gate");
        SUFFIX.put(Shape.FENCE, "_fence");
        SUFFIX.put(Shape.PRESSURE_PLATE, "_pressure_plate");
        SUFFIX.put(Shape.TRAPDOOR, "_trapdoor");
        SUFFIX.put(Shape.DOOR, "_door");
        SUFFIX.put(Shape.BUTTON, "_button");
        SUFFIX.put(Shape.STAIRS, "_stairs");
        SUFFIX.put(Shape.SLAB, "_slab");
        SUFFIX.put(Shape.WALL, "_wall");
        SUFFIX.put(Shape.PANE, "_pane");
    }

    private static final class Builder {
        final ResourceLocation id;
        boolean wood;
        final EnumMap<Shape, Block> blocks = new EnumMap<>(Shape.class);

        Builder(ResourceLocation id, boolean wood) {
            this.id = id;
            this.wood = wood;
        }
    }

    static @Nullable Shape classShape(Block b, String path) {
        if (b instanceof StairBlock) return Shape.STAIRS;
        if (b instanceof SlabBlock) return Shape.SLAB;
        if (b instanceof WallBlock) return Shape.WALL;
        if (b instanceof FenceGateBlock) return Shape.FENCE_GATE;
        if (b instanceof FenceBlock) return Shape.FENCE;
        if (b instanceof DoorBlock) return Shape.DOOR;
        if (b instanceof TrapDoorBlock) return Shape.TRAPDOOR;
        if (b instanceof ButtonBlock) return Shape.BUTTON;
        if (b instanceof BasePressurePlateBlock) return Shape.PRESSURE_PLATE;
        if (b instanceof WallHangingSignBlock) return Shape.WALL_HANGING_SIGN;
        if (b instanceof CeilingHangingSignBlock) return Shape.HANGING_SIGN;
        if (b instanceof WallSignBlock) return Shape.WALL_SIGN;
        if (b instanceof StandingSignBlock) return Shape.SIGN;
        if (b instanceof IronBarsBlock && path.endsWith("_pane")) return Shape.PANE;
        return null;
    }

    static FamilyIndex build(List<Override> overrides) {
        // sorted by id so that both sides build the same index regardless of registration order
        TreeMap<ResourceLocation, Block> byId = new TreeMap<>();
        for (Block b : BuiltInRegistries.BLOCK) byId.put(BuiltInRegistries.BLOCK.getKey(b), b);

        Map<Block, Shape> assignedShape = new IdentityHashMap<>();
        Map<Block, ResourceLocation> assignedFamily = new IdentityHashMap<>();
        TreeMap<ResourceLocation, Builder> builders = new TreeMap<>();

        // 1. datapack overrides
        for (Override o : overrides) {
            Builder fb = builders.computeIfAbsent(o.family(), k -> new Builder(k, o.wood()));
            o.blocks().forEach((shape, blockId) -> {
                Block b = byId.get(blockId);
                if (b != null && !assignedShape.containsKey(b) && !fb.blocks.containsKey(shape)) {
                    assign(fb, shape, b, assignedShape, assignedFamily);
                }
            });
        }

        // 2. wood types: planks and the pillar variants with their irregular names
        List<WoodType> woods = new ArrayList<>(WoodType.values().toList());
        woods.sort((a, c) -> a.name().compareTo(c.name()));
        for (WoodType wt : woods) {
            ResourceLocation wid = ResourceLocation.tryParse(wt.name());
            if (wid == null) continue;
            String ns = wid.getNamespace(), n = wid.getPath();
            Block planks = byId.get(ResourceLocation.fromNamespaceAndPath(ns, n + "_planks"));
            if (planks == null || assignedShape.containsKey(planks)) continue;
            Builder fb = builders.computeIfAbsent(ResourceLocation.fromNamespaceAndPath(ns, n), k -> new Builder(k, true));
            fb.wood = true;
            assign(fb, Shape.FULL, planks, assignedShape, assignedFamily);
            tryAssign(fb, Shape.LOG, byId, assignedShape, assignedFamily, ns, n + "_log", n + "_stem", n + "_block");
            tryAssign(fb, Shape.WOOD, byId, assignedShape, assignedFamily, ns, n + "_wood", n + "_hyphae");
            tryAssign(fb, Shape.STRIPPED_LOG, byId, assignedShape, assignedFamily, ns,
                    "stripped_" + n + "_log", "stripped_" + n + "_stem", "stripped_" + n + "_block");
            tryAssign(fb, Shape.STRIPPED_WOOD, byId, assignedShape, assignedFamily, ns,
                    "stripped_" + n + "_wood", "stripped_" + n + "_hyphae");
        }

        // 3. shaped blocks: shape from the class, material from the name
        for (Map.Entry<ResourceLocation, Block> e : byId.entrySet()) {
            Block b = e.getValue();
            if (assignedShape.containsKey(b)) continue;
            String ns = e.getKey().getNamespace(), path = e.getKey().getPath();
            Shape shape = classShape(b, path);
            if (shape == null) continue;
            String suffix = SUFFIX.get(shape);
            if (!path.endsWith(suffix) || path.length() == suffix.length()) continue;
            String base = path.substring(0, path.length() - suffix.length());
            ResourceLocation famId = ResourceLocation.fromNamespaceAndPath(ns, base);
            Builder fb = builders.get(famId);
            if (fb == null) {
                Block full = null;
                ResourceLocation fullId = null;
                for (String cand : new String[]{base, base + "s", base + "_planks", base + "_block"}) {
                    ResourceLocation cid = ResourceLocation.fromNamespaceAndPath(ns, cand);
                    Block c = byId.get(cid);
                    if (c != null && !assignedShape.containsKey(c) && classShape(c, cand) == null) {
                        full = c;
                        fullId = cid;
                        break;
                    }
                }
                // a FULL block named like an existing family (e.g. "stone_bricks") joins that family's id
                if (fullId != null && builders.containsKey(fullId)) {
                    fb = builders.get(fullId);
                } else {
                    ResourceLocation id = fullId != null ? fullId : famId;
                    fb = builders.computeIfAbsent(id, k -> new Builder(k, false));
                    if (full != null) {
                        assign(fb, Shape.FULL, full, assignedShape, assignedFamily);
                        if (fullId.getPath().endsWith("_planks")) fb.wood = true;
                    }
                }
                // later shapes with the same base find the family under the base id too
                if (!fb.id.equals(famId)) builders.putIfAbsent(famId, fb);
            }
            if (!fb.blocks.containsKey(shape)) assign(fb, shape, b, assignedShape, assignedFamily);
        }

        // 4. pillars of families created in step 3 (modded woods without a registered WoodType)
        for (Map.Entry<ResourceLocation, Block> e : byId.entrySet()) {
            Block b = e.getValue();
            if (assignedShape.containsKey(b)) continue;
            String ns = e.getKey().getNamespace(), path = e.getKey().getPath();
            boolean stripped = path.startsWith("stripped_");
            String p = stripped ? path.substring("stripped_".length()) : path;
            Shape shape = null;
            String base = null;
            for (String s : new String[]{"_log", "_stem"}) {
                if (p.endsWith(s)) {
                    shape = stripped ? Shape.STRIPPED_LOG : Shape.LOG;
                    base = p.substring(0, p.length() - s.length());
                }
            }
            for (String s : new String[]{"_wood", "_hyphae"}) {
                if (p.endsWith(s)) {
                    shape = stripped ? Shape.STRIPPED_WOOD : Shape.WOOD;
                    base = p.substring(0, p.length() - s.length());
                }
            }
            if (shape == null || base.isEmpty()) continue;
            Builder fb = builders.get(ResourceLocation.fromNamespaceAndPath(ns, base));
            if (fb != null && fb.wood && !fb.blocks.containsKey(shape)) assign(fb, shape, b, assignedShape, assignedFamily);
        }

        // 5. drop weak families (no FULL and a single shape): their blocks become singletons
        Map<ResourceLocation, Builder> unique = new TreeMap<>();
        for (Builder fb : builders.values()) unique.put(fb.id, fb);
        for (Builder fb : unique.values()) {
            if (!fb.wood && !fb.blocks.containsKey(Shape.FULL) && fb.blocks.size() < 2) {
                for (Block b : fb.blocks.values()) {
                    assignedShape.remove(b);
                    assignedFamily.remove(b);
                }
                fb.blocks.clear();
            }
        }

        Map<ResourceLocation, Family> families = new TreeMap<>();
        Map<Block, Member> members = new IdentityHashMap<>();
        for (Builder fb : unique.values()) {
            if (fb.blocks.isEmpty()) continue;
            Family f = new Family(fb.id, fb.blocks, fb.wood);
            families.put(f.id(), f);
            f.blocks().forEach((s, b) -> members.put(b, new Member(f, s)));
        }

        // 6. every other obtainable block is its own family
        for (Map.Entry<ResourceLocation, Block> e : byId.entrySet()) {
            Block b = e.getValue();
            if (members.containsKey(b) || b.asItem() == Items.AIR || families.containsKey(e.getKey())) continue;
            Family f = new Family(e.getKey(), Map.of(Shape.FULL, b), false);
            families.put(f.id(), f);
            members.put(b, new Member(f, Shape.FULL));
        }
        return new FamilyIndex(Collections.unmodifiableMap(members), Collections.unmodifiableMap(families));
    }

    private static void assign(Builder fb, Shape s, Block b, Map<Block, Shape> shapes, Map<Block, ResourceLocation> fams) {
        fb.blocks.put(s, b);
        shapes.put(b, s);
        fams.put(b, fb.id);
    }

    private static void tryAssign(Builder fb, Shape s, Map<ResourceLocation, Block> byId, Map<Block, Shape> shapes,
                                  Map<Block, ResourceLocation> fams, String ns, String... names) {
        if (fb.blocks.containsKey(s)) return;
        for (String n : names) {
            Block b = byId.get(ResourceLocation.fromNamespaceAndPath(ns, n));
            if (b != null && !shapes.containsKey(b)) {
                assign(fb, s, b, shapes, fams);
                return;
            }
        }
    }

    /** Debug helper: family ids with their shapes. */
    public Map<String, List<String>> describe() {
        Map<String, List<String>> out = new HashMap<>();
        for (Family f : families.values()) {
            List<String> l = new ArrayList<>();
            f.blocks().forEach((s, b) -> l.add(s + "=" + BuiltInRegistries.BLOCK.getKey(b)));
            out.put(f.id().toString(), l);
        }
        return out;
    }
}
