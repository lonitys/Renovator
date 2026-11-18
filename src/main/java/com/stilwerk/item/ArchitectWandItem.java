package com.stilwerk.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The architect's wand: selects, swaps styles and places blueprints. The behaviour is client-driven
 * (selection, menus, preview); the server only answers requests. It never breaks or uses blocks.
 */
public class ArchitectWandItem extends Item {
    public ArchitectWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        if (ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return WandHooks.clientUseOn.apply(ctx);
        return InteractionResult.CONSUME; // blocks doors, chests ... from reacting to wand clicks
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) {
            WandHooks.clientUse.accept(player, hand);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.stilwerk.architect_wand.tip1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.stilwerk.architect_wand.tip2").withStyle(ChatFormatting.GRAY));
    }
}
