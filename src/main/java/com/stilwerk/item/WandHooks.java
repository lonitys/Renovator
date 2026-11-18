package com.stilwerk.item;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

/** Client behaviour of the wand, installed by the client setup so common code never touches client classes. */
public final class WandHooks {
    private WandHooks() {}

    public static Function<UseOnContext, InteractionResult> clientUseOn = ctx -> InteractionResult.PASS;
    public static BiConsumer<Player, InteractionHand> clientUse = (p, h) -> {};
}
