package com.renovator.interior;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * Where the furniture comes from: {@link #MODS} (every installed furniture mod, vanilla only where none fits),
 * one furniture mod by its id, or {@link #VANILLA} only. The same choice gives the same room on client and server.
 */
public final class FurnitureChoice {
    private FurnitureChoice() {}

    public static final String MODS = "mods", VANILLA = "vanilla";
    /** Furniture mods with modules, in menu order. */
    static final List<String> FURNITURE_MODS = List.of("mcwfurnitures", "refurbished_furniture");

    /** The choices available with the installed mods; the first one is the default. */
    public static List<String> options() {
        List<String> mods = new ArrayList<>();
        for (String id : FURNITURE_MODS) if (ModList.get().isLoaded(id)) mods.add(id);
        List<String> out = new ArrayList<>();
        if (mods.size() > 1) out.add(MODS);
        out.addAll(mods);
        out.add(VANILLA);
        return out;
    }

    /** A known choice, else the default (also for choices whose mod is not installed here). */
    public static String normalize(String choice) {
        List<String> o = options();
        return o.contains(choice) ? choice : o.get(0);
    }

    public static String next(String choice) {
        List<String> o = options();
        return o.get((o.indexOf(normalize(choice)) + 1) % o.size());
    }

    /** May this module be used at all? */
    static boolean allowed(Module m, String choice) {
        if (!m.modded()) return true;
        return switch (choice) {
            case MODS -> true;
            case VANILLA -> false;
            default -> m.requires().contains(choice);
        };
    }

    /** Is this module preferred over vanilla ones (used first, vanilla only where it does not fit)? */
    static boolean preferred(Module m, String choice) {
        return !choice.equals(VANILLA) && m.modded() && allowed(m, choice);
    }

    public static Component label(String choice) {
        return switch (choice) {
            case MODS -> Component.translatable("renovator.interior.furniture.mods");
            case VANILLA -> Component.translatable("renovator.interior.furniture.vanilla");
            default -> Component.literal(ModList.get().getModContainerById(choice).map(c -> c.getModInfo().getDisplayName()).orElse(choice));
        };
    }
}
