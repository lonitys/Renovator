package com.stilwerk.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.stilwerk.Stilwerk;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Facing probe ({@code -Dstilwerk.probe=true}): every furniture block with facing = north, east, south, west (left to right),
 * one row per block, rows stepped like bleachers, photographed from the south. The column showing its front to the camera
 * is the facing value for "front faces south".
 */
@EventBusSubscriber(modid = Stilwerk.MODID, value = Dist.CLIENT)
public final class FacingProbe {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("stilwerk.probe");
    private static final int PER_PAGE = 6;
    private static final String[] BLOCKS = {
            "mcwfurnitures:oak_drawer", "mcwfurnitures:oak_wardrobe", "mcwfurnitures:oak_desk", "mcwfurnitures:oak_chair",
            "mcwfurnitures:red_couch", "mcwfurnitures:oak_bookshelf", "mcwfurnitures:oak_counter", "mcwfurnitures:oak_kitchen_cabinet",
            "mcwfurnitures:oak_kitchen_sink", "mcwfurnitures:oak_triple_drawer",
            "refurbished_furniture:oak_drawer", "refurbished_furniture:oak_storage_cabinet", "refurbished_furniture:oak_desk",
            "refurbished_furniture:computer", "refurbished_furniture:oak_chair", "refurbished_furniture:red_sofa",
            "refurbished_furniture:television", "refurbished_furniture:oak_kitchen_cabinetry", "refurbished_furniture:oak_kitchen_drawer",
            "refurbished_furniture:light_stove", "refurbished_furniture:light_range_hood", "refurbished_furniture:oak_kitchen_sink",
            "refurbished_furniture:light_freezer", "refurbished_furniture:light_fridge", "refurbished_furniture:red_bath",
            "refurbished_furniture:white_toilet", "refurbished_furniture:white_basin", "refurbished_furniture:workbench"};
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    private record Shot(String name, double x, double y, double z) {}

    private static int ticks = -1;
    private static final List<Shot> shots = new ArrayList<>();

    private FacingProbe() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) return;
        ticks++;
        mc.mouseHandler.releaseMouse();
        mc.player.setYRot(180);
        mc.player.setXRot(28);
        mc.player.yRotO = 180;
        mc.player.xRotO = 28;
        if (ticks == 20) {
            mc.options.hideGui = true;
            BlockPos o = mc.player.blockPosition();
            server.execute(() -> build(server, mc, o));
        }
        int start = 100, per = 30;
        if (ticks >= start && !shots.isEmpty()) {
            int i = (ticks - start) / per, step = (ticks - start) % per;
            if (i >= shots.size()) {
                if (step == 0) mc.stop();
                return;
            }
            Shot s = shots.get(i);
            if (step == 0) {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.connection.teleport(s.x(), s.y(), s.z(), 180, 28);
                });
            } else if (step == per - 6) {
                Screenshot.grab(mc.gameDirectory, "probe_" + s.name() + ".png", mc.getMainRenderTarget(), m -> {});
            }
        }
    }

    private static void build(MinecraftServer server, Minecraft mc, BlockPos o) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
        player.setGameMode(GameType.CREATIVE);
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        for (int i = 0; i < BLOCKS.length; i++) {
            int page = i / PER_PAGE, r = i % PER_PAGE;
            // rows go back (north) and up: row r at z = -2r, y = r
            BlockPos row = o.offset(page * 14, r, 8 - 2 * r);
            for (int dx = -1; dx <= 8; dx++) {
                level.setBlock(row.offset(dx, -1, 0), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                level.setBlock(row.offset(dx, -1, -1), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
            for (int f = 0; f < 4; f++) {
                String s = BLOCKS[i] + "[facing=" + FACINGS[f] + "]";
                BlockState state;
                try {
                    state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
                } catch (CommandSyntaxException ex) {
                    continue; // e.g. the desk only has north/east
                }
                level.setBlock(row.offset(f * 2, 0, 0), state, 2);
            }
            LOG.info("[probe] page {} row {} (front row = 0) {}", page, r, BLOCKS[i]);
        }
        int pages = (BLOCKS.length + PER_PAGE - 1) / PER_PAGE;
        for (int p = 0; p < pages; p++) shots.add(new Shot("page" + p, o.getX() + p * 14 + 3.5, o.getY() + 6.5, o.getZ() + 15.5));
    }
}
