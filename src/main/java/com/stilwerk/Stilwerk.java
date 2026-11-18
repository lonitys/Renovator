package com.stilwerk;

import com.stilwerk.config.ClientConfig;
import com.stilwerk.config.ServerConfig;
import com.stilwerk.item.ArchitectWandItem;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Stilwerk.MODID)
public final class Stilwerk {
    public static final String MODID = "stilwerk";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<ArchitectWandItem> ARCHITECT_WAND =
            ITEMS.register("architect_wand", () -> new ArchitectWandItem(new Item.Properties().stacksTo(1)));

    public Stilwerk(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(Stilwerk::creativeTab);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
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
