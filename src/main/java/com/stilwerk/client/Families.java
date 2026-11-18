package com.stilwerk.client;

import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.mix.Mixes;
import com.stilwerk.swap.Target;
import com.stilwerk.swap.Template;
import java.util.Map;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Display names and icons for families, mixes, templates and targets. */
public final class Families {
    private Families() {}

    public static Component name(Family f) {
        String key = f.translationKey();
        return I18n.exists(key) ? Component.translatable(key) : Component.translatable(f.icon().getDescriptionId());
    }

    public static ItemStack icon(Family f) {
        ItemStack s = new ItemStack(f.icon().asItem());
        return s.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.BARRIER) : s;
    }

    public static Component name(MixPalette p) {
        return Mixes.nameIsKey(p) ? Component.translatable(p.name()) : Component.literal(p.name());
    }

    public static Component name(Template t) {
        return t.nameIsKey() ? Component.translatable(t.name()) : Component.literal(t.name());
    }

    public static Component target(Target t, Map<String, MixPalette> mixes) {
        return switch (t.kind()) {
            case KEEP -> Component.translatable("stilwerk.target.keep");
            case FAMILY -> {
                Family f = t.family().map(id -> FamilyIndex.get().byId(id)).orElse(null);
                yield f == null ? Component.literal("?") : name(f);
            }
            case MIX -> {
                MixPalette p = t.mix().map(mixes::get).orElse(null);
                yield Component.translatable("stilwerk.target.mix", p == null ? Component.literal(t.mix().orElse("?")) : name(p));
            }
        };
    }

    public static ItemStack targetIcon(Target t, Map<String, MixPalette> mixes) {
        return switch (t.kind()) {
            case KEEP -> ItemStack.EMPTY;
            case FAMILY -> t.family().map(id -> FamilyIndex.get().byId(id)).map(Families::icon).orElse(ItemStack.EMPTY);
            case MIX -> t.mix().map(mixes::get).map(p -> FamilyIndex.get().byId(p.base())).map(Families::icon).orElse(ItemStack.EMPTY);
        };
    }
}
