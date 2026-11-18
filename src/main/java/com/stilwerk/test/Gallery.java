package com.stilwerk.test;

import com.mojang.logging.LogUtils;
import com.stilwerk.Stilwerk;
import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.core.Applier;
import com.stilwerk.core.CreativeGate;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import com.stilwerk.detail.DetailPlanner;
import com.stilwerk.detail.DetailTypes.Choice;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.interior.Module;
import com.stilwerk.interior.ModuleLibrary;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Orientation gallery ({@code -Dstilwerk.gallery=true}): every furniture module against a north wall (front to the south),
 * and three houses with all detail suggestions applied in variant 1, 2 and 3. Screenshots, then quits.
 */
@EventBusSubscriber(modid = Stilwerk.MODID, value = Dist.CLIENT)
public final class Gallery {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("stilwerk.gallery");
    private static final int SHOT_WIDTH = 22;

    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {}

    private static int ticks = -1;
    private static final List<Shot> shots = new ArrayList<>();
    private static float yaw = Float.NaN, pitch;

    private Gallery() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) return;
        ticks++;
        mc.mouseHandler.releaseMouse();
        if (!Float.isNaN(yaw)) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
        }
        if (ticks == 20) {
            BlockPos o = mc.player.blockPosition();
            mc.options.hideGui = true;
            server.execute(() -> build(server, mc, o));
        }
        int start = 120, per = 30;
        if (ticks >= start && !shots.isEmpty()) {
            int i = (ticks - start) / per, step = (ticks - start) % per;
            if (i >= shots.size()) {
                if (step == 0) mc.stop();
                return;
            }
            Shot s = shots.get(i);
            if (step == 0) {
                yaw = s.yaw();
                pitch = s.pitch();
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.connection.teleport(s.x(), s.y(), s.z(), s.yaw(), s.pitch());
                });
            } else if (step == per - 6) {
                Screenshot.grab(mc.gameDirectory, "gallery_" + s.name() + ".png", mc.getMainRenderTarget(), m -> {});
            }
        }
    }

    private static void build(MinecraftServer server, Minecraft mc, BlockPos o) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
        player.setGameMode(GameType.CREATIVE);
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        Family spruce = FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace("spruce"));
        // modules: rows of SHOT_WIDTH blocks, each row against its own north wall
        List<Module> mods = new ArrayList<>();
        for (String c : ModuleLibrary.categories()) mods.addAll(ModuleLibrary.available(c));
        int row = 0, x = 0;
        List<int[]> rowStarts = new ArrayList<>();
        BlockPos base = o.offset(4, 0, 6);
        rowStarts.add(new int[]{0});
        for (Module m : mods) {
            int w = m.size().getX();
            if (x + w > SHOT_WIDTH) {
                row++;
                x = 0;
            }
            BlockPos at = base.offset(x, 0, row * 13);
            Blueprint bp = m.blueprint(DyeColor.RED, spruce, Rotation.NONE);
            bp.forEach((p, s, be) -> {
                if (!s.isAir()) level.setBlock(at.offset(p).offset(0, 0, 1), s, 3);
            });
            LOG.info("[gallery] row {} x {} {}", row, x, m.id());
            x += w + 1;
        }
        for (int r = 0; r <= row; r++) {
            BlockPos w0 = base.offset(-1, -1, r * 13);
            for (int dx = 0; dx <= SHOT_WIDTH + 1; dx++) {
                for (int dz = 0; dz <= 12; dz++) level.setBlock(w0.offset(dx, 0, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                for (int dy = 1; dy <= 3; dy++) level.setBlock(w0.offset(dx, dy, 0), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
            shots.add(new Shot("modules_" + r, base.getX() + SHOT_WIDTH / 2.0, base.getY() + 3.6, base.getZ() + r * 13 + 11.5, 180, 20));
        }
        // detail houses with variant 0, 1, 2 for every suggestion
        for (int v = 0; v < 3; v++) {
            BlockPos h = o.offset(-40 + v * 14, 0, 6);
            house(level, h);
            ResolvedSelection sel = new Selection.Box(h.offset(-2, 0, -2), h.offset(10, 8, 10)).resolve(level, 100_000);
            List<Suggestion> found = DetailPlanner.find(level, sel, 3);
            List<Choice> choices = new ArrayList<>();
            for (Suggestion s : found) choices.add(new Choice(s.rule(), s.anchor(), Math.min(v, s.variants().size() - 1)));
            Applier.applyNow(level, DetailPlanner.combine(found, choices), CreativeGate.INSTANCE);
            shots.add(new Shot("details_v" + (v + 1) + "_front", h.getX() + 4.5, h.getY() + 3, h.getZ() + 17, 180, 12));
            shots.add(new Shot("details_v" + (v + 1) + "_corner", h.getX() - 5.5, h.getY() + 5, h.getZ() + 14, -135, 20));
        }
        LOG.info("[gallery] built {} modules in {} rows, {} shots", mods.size(), row + 1, shots.size());
    }

    /** Like DetailTests.house, but bigger: stone base, plank walls with log corners, door + windows south, eave stairs north and south. */
    private static void house(ServerLevel level, BlockPos h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 6; z++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 6;
                boolean corner = (x == 0 || x == 8) && (z == 0 || z == 6);
                if (edge) level.setBlock(h.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                for (int y = 1; y <= 3; y++) {
                    if (edge) level.setBlock(h.offset(x, y, z), (corner ? Blocks.SPRUCE_LOG : Blocks.SPRUCE_PLANKS).defaultBlockState(), 2);
                }
                level.setBlock(h.offset(x, 4, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
            }
        for (int x = -1; x <= 9; x++) {
            level.setBlock(h.offset(x, 4, -1), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), 2);
            level.setBlock(h.offset(x, 4, 7), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), 2);
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(h.offset(4, 1, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(h.offset(4, 2, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        for (int x : new int[]{2, 6}) {
            level.setBlock(h.offset(x, 2, 6), Blocks.GLASS_PANE.defaultBlockState(), 2);
            level.setBlock(h.offset(x, 2, 0), Blocks.GLASS_PANE.defaultBlockState(), 2);
        }
        level.setBlock(h.offset(8, 2, 3), Blocks.GLASS_PANE.defaultBlockState(), 2);
    }

    static Block unused() {
        return Blocks.AIR;
    }
}
