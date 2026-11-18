package com.renovator.command;

import com.mojang.brigadier.CommandDispatcher;
import com.renovator.Renovator;
import com.renovator.net.ServerOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /renovator undo} – undo your last Renovator operation. */
@EventBusSubscriber(modid = Renovator.MODID)
public final class RenovatorCommand {
    private RenovatorCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("renovator")
                .then(Commands.literal("undo").executes(ctx -> {
                    ServerOps.undo(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
