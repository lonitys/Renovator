package com.stilwerk.client;

import com.stilwerk.client.screen.BlueprintMenuScreen;
import com.stilwerk.client.screen.SwapScreen;
import com.stilwerk.item.WandMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

/** What a right-click with the wand does on the client, per mode. */
public final class WandClient {
    private WandClient() {}

    public static InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        switch (ClientState.mode) {
            case SELECT -> {
                if (p.isShiftKeyDown()) ClientState.setFlood(ctx.getClickedPos());
                else ClientState.setCorner(ctx.getClickedPos());
            }
            case SWAP -> openSwap();
            case BLUEPRINT -> Minecraft.getInstance().setScreen(new BlueprintMenuScreen());
        }
        return InteractionResult.SUCCESS;
    }

    public static void use(Player p, InteractionHand hand) {
        switch (ClientState.mode) {
            case SELECT -> {
                if (p.isShiftKeyDown()) ClientState.clear();
                else ClientState.actionbar(Component.translatable("stilwerk.hint.select"));
            }
            case SWAP -> openSwap();
            case BLUEPRINT -> Minecraft.getInstance().setScreen(new BlueprintMenuScreen());
        }
    }

    public static void openSwap() {
        if (PlacementController.active()) {
            Minecraft.getInstance().setScreen(SwapScreen.forBlueprint());
        } else if (ClientState.selection() != null) {
            Minecraft.getInstance().setScreen(SwapScreen.forSelection());
        } else {
            ClientState.actionbar(Component.translatable("stilwerk.hint.select_first"));
        }
    }

    public static boolean holdingWand() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getMainHandItem().getItem() instanceof com.stilwerk.item.ArchitectWandItem;
    }

    public static void setMode(WandMode m) {
        ClientState.mode = m;
        ClientState.actionbar(Component.translatable("stilwerk.mode.changed", Component.translatable(m.translationKey())));
    }
}
