package com.renovator.projection;

import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A build that stays in the world as ghosts until it is done: the changes of an action (old state = what was expected
 * there, new state = the target), grouped like the change set (door halves, bed parts go together).
 */
public record Projection(UUID id, String labelKey, String labelArg, ResourceKey<Level> dimension, ChangeSet set, long created) {

    public static Projection of(ChangeSet set, String labelKey, String labelArg, ResourceKey<Level> dimension) {
        return new Projection(UUID.randomUUID(), labelKey, labelArg, dimension, set, System.currentTimeMillis());
    }

    public int cellCount() {
        return set.size();
    }

    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Change c : set.changes()) {
            BlockPos p = c.pos();
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("label", labelKey);
        t.putString("arg", labelArg);
        t.putString("dim", dimension.location().toString());
        t.putLong("created", created);
        t.put("set", set.save());
        return t;
    }

    public static Projection load(CompoundTag t) {
        return new Projection(t.getUUID("id"), t.getString("label"), t.getString("arg"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                ChangeSet.load(t.getCompound("set"), BuiltInRegistries.BLOCK.asLookup()), t.getLong("created"));
    }
}
