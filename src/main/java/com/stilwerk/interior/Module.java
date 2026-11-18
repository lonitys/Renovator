package com.stilwerk.interior;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintMeta;
import com.stilwerk.blueprint.Transform;
import com.stilwerk.family.Family;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A handmade furniture group. Authored in oak with the token COLOR for dye colours, front towards south (+z),
 * back at z=0 against the wall. {@code placement}: wall, corner, window, centre or free.
 */
public final class Module {
    public record Cell(int x, int y, int z, String state) {}

    private final String id, category, placement;
    private final Vec3i size;
    private final List<String> requires;
    private final List<Cell> cells;
    private final int weight;
    private final Map<String, Blueprint> cache = new HashMap<>();

    public Module(String id, String category, Vec3i size, String placement, List<String> requires, List<Cell> cells, int weight) {
        this.id = id;
        this.category = category;
        this.size = size;
        this.placement = placement;
        this.requires = List.copyOf(requires);
        this.cells = List.copyOf(cells);
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public String category() {
        return category;
    }

    public String placement() {
        return placement;
    }

    public Vec3i size() {
        return size;
    }

    public List<String> requires() {
        return requires;
    }

    /** True for modules made of modded furniture (preferred when available). */
    public boolean modded() {
        return !requires.isEmpty();
    }

    public int weight() {
        return weight;
    }

    static @Nullable BlockState parse(String s, DyeColor color) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s.replace("COLOR", color.getSerializedName()), false).blockState();
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** True when every block of the module exists (with a test colour). */
    boolean resolvable() {
        for (Cell c : cells) if (parse(c.state(), DyeColor.WHITE) == null) return false;
        return true;
    }

    /** The module in the house's wood and colour, rotated (cached). */
    public synchronized Blueprint blueprint(DyeColor color, Family wood, Rotation rot) {
        String key = color.getSerializedName() + "|" + wood.id() + "|" + rot;
        Blueprint bp = cache.get(key);
        if (bp == null) {
            Blueprint.Builder b = new Blueprint.Builder(size, BlueprintMeta.UNNAMED);
            for (Cell c : cells) {
                BlockState s = parse(c.state(), color);
                if (s != null) b.set(c.x(), c.y(), c.z(), WoodAdapter.adapt(s, wood), null);
            }
            bp = Transform.apply(b.build(), rot, Mirror.NONE);
            cache.put(key, bp);
        }
        return bp;
    }
}
