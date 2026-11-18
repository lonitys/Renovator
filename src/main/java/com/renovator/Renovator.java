package com.renovator;

import com.renovator.config.ClientConfig;
import com.renovator.config.ServerConfig;
import com.renovator.item.ArchitectWandItem;
import com.renovator.family.FamilyOverrides;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Renovator.MODID)
public final class Renovator {
    public static final String MODID = "renovator";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<ArchitectWandItem> ARCHITECT_WAND =
            ITEMS.register("architect_wand", () -> new ArchitectWandItem(new Item.Properties().stacksTo(1)));

    public Renovator(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(Renovator::creativeTab);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new FamilyOverrides());
            e.addListener(new com.renovator.swap.Templates.Loader());
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(ARCHITECT_WAND.get());
        }
    }
}
