package com.stilwerk.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stilwerk.Stilwerk;
import com.stilwerk.net.ServerOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /stilwerk undo} – undo your last Stilwerk operation. */
@EventBusSubscriber(modid = Stilwerk.MODID)
public final class StilwerkCommand {
    private StilwerkCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("stilwerk")
                .then(Commands.literal("undo").executes(ctx -> {
                    ServerOps.undo(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
