package com.stilwerk.core;

import com.stilwerk.Stilwerk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Stilwerk.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        Applier.tick(e.getServer());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        Applier.clear();
    }
}
