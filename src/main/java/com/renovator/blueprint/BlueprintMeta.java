package com.renovator.blueprint;

import net.minecraft.nbt.CompoundTag;

/** Name and origin of a blueprint (stored in the "renovator" compound of our .nbt files). */
public record BlueprintMeta(String name, String author, long created, String source) {
    public static final BlueprintMeta UNNAMED = new BlueprintMeta("", "", 0L, "renovator");

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("author", author);
        t.putLong("created", created);
        t.putString("source", source);
        t.putInt("format", 1);
        return t;
    }

    public static BlueprintMeta load(CompoundTag t) {
        return new BlueprintMeta(t.getString("name"), t.getString("author"), t.getLong("created"),
                t.contains("source") ? t.getString("source") : "renovator");
    }

    public BlueprintMeta withName(String n) {
        return new BlueprintMeta(n, author, created, source);
    }
}
