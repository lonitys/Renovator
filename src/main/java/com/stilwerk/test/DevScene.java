package com.stilwerk.test;

import com.mojang.logging.LogUtils;
import com.stilwerk.Stilwerk;
import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintMeta;
import com.stilwerk.client.ClientLibrary;
import com.stilwerk.client.ClientState;
import com.stilwerk.client.PlacementController;
import com.stilwerk.client.PreviewController;
import com.stilwerk.client.screen.LibraryScreen;
import com.stilwerk.client.screen.MaterialReportScreen;
import com.stilwerk.client.screen.MixEditorScreen;
import com.stilwerk.client.screen.ModeWheelScreen;
import com.stilwerk.client.screen.SwapScreen;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import com.stilwerk.item.WandMode;
import com.stilwerk.mix.Mixes;
import com.stilwerk.net.SmallPayloads;
import com.stilwerk.swap.SwapMapping;
import com.stilwerk.swap.Target;
import com.stilwerk.swap.Template;
import com.stilwerk.swap.Templates;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Dev-only client check ({@code -Dstilwerk.devScene=true}, singleplayer): builds a Japanese-style hut, drives the
 * selection, swap screen, preview, apply, mix, mix editor, copy, library, placement and undo through the real client
 * code, writes {@code run/screenshots/stilwerk_*.png} and logs "CHECK ... OK/FAIL", then quits.
 */
@EventBusSubscriber(modid = Stilwerk.MODID, value = Dist.CLIENT)
public final class DevScene {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("stilwerk.devScene");

    private static int ticks = -1;
    private static BlockPos o; // hut corner
    private static BlockPos copyAt;
    private static int fails;

    private DevScene() {}

    private static ResourceLocation mc(String p) {
        return ResourceLocation.withDefaultNamespace(p);
    }

    private static void check(String name, boolean ok, Object detail) {
        if (!ok) fails++;
        LOG.info("[stilwerk dev scene] CHECK {} {} ({})", name, ok ? "OK" : "FAIL", detail);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "stilwerk_" + name + ".png", mc.getMainRenderTarget(),
                m -> LOG.info("[stilwerk dev scene] {}", m.getString()));
    }

    private static float viewYaw = Float.NaN, viewPitch;

    private static void teleport(MinecraftServer server, Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        viewYaw = yaw;
        viewPitch = pitch;
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p == null) return;
            p.getAbilities().flying = true; // stay where the camera was put (creative players fall otherwise)
            p.onUpdateAbilities();
            p.connection.teleport(x, y, z, yaw, pitch);
        });
    }

    private static Selection hutBox() {
        return new Selection.Box(o.offset(-1, 0, -1), o.offset(8, 7, 7));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) return;
        ticks++;
        // the scene must not depend on whatever the real mouse does while the window has focus
        mc.mouseHandler.releaseMouse();
        if (!Float.isNaN(viewYaw)) {
            mc.player.setYRot(viewYaw);
            mc.player.setXRot(viewPitch);
            mc.player.yRotO = viewYaw;
            mc.player.xRotO = viewPitch;
        }
        try {
            step(mc, server);
        } catch (RuntimeException ex) {
            fails++;
            LOG.error("[stilwerk dev scene] step {} threw", ticks, ex);
        }
    }

    private static void step(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 20 -> {
                o = mc.player.blockPosition().offset(3, 0, 6);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
                    p.setGameMode(GameType.CREATIVE);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Stilwerk.ARCHITECT_WAND.get()));
                    build(server.overworld(), o);
                });
            }
            case 40 -> {
                teleport(server, mc, o.getX() + 3.5, o.getY() + 2, o.getZ() - 9, 0, 12);
                ClientState.mode = WandMode.SELECT;
                Selection.Box b = (Selection.Box) hutBox();
                ClientState.setBox(BoundingBox.fromCorners(b.a(), b.b()));
            }
            case 60 -> shot(mc, "selection");
            case 65 -> mc.setScreen(SwapScreen.forSelection());
            case 85 -> shot(mc, "swapscreen");
            case 90 -> {
                Template t = Templates.builtIn().stream().filter(x -> x.id().equals("stilwerk:japanese_to_nordic")).findFirst().orElseThrow();
                ClientState.mapping = t.applyTo(SwapMapping.EMPTY, List.of(mc("acacia"), mc("jungle"), mc("cherry"), mc("oak")));
                mc.setScreen(SwapScreen.forSelection());
            }
            case 105 -> shot(mc, "swapscreen_template");
            case 110 -> {
                mc.setScreen(null);
                PreviewController.showSwap(hutBox(), ClientState.mapping, Mixes.all(), 7, false);
                check("preview_active", PreviewController.active() && PreviewController.lastChanges() > 50, PreviewController.lastChanges());
            }
            case 130 -> shot(mc, "preview");
            case 132 -> PreviewController.toggleBeforeAfter();
            case 150 -> shot(mc, "preview_before");
            case 152 -> {
                PreviewController.toggleBeforeAfter();
                PreviewController.confirm();
            }
            case 190 -> {
                Set<Block> found = blocksInHut(mc);
                check("no_acacia_left", !found.contains(Blocks.ACACIA_PLANKS) && !found.contains(Blocks.ACACIA_LOG)
                        && !found.contains(Blocks.JUNGLE_STAIRS), found);
                check("spruce_present", found.contains(Blocks.SPRUCE_PLANKS) && found.contains(Blocks.DARK_OAK_STAIRS), found);
                check("door_whole", mc.level.getBlockState(o.offset(3, 2, 0)).is(Blocks.SPRUCE_DOOR)
                        && mc.level.getBlockState(o.offset(3, 3, 0)).is(Blocks.SPRUCE_DOOR), mc.level.getBlockState(o.offset(3, 3, 0)));
                shot(mc, "after");
            }
            case 195 -> PreviewController.applyDirect(hutBox(), SwapMapping.EMPTY.with(mc("stone_bricks"), Target.mix("stilwerk:old_mossy")),
                    Mixes.all(), 11, false);
            case 230 -> {
                Set<Block> base = new HashSet<>();
                for (int x = 0; x <= 7; x++) base.add(mc.level.getBlockState(o.offset(x, 0, 0)).getBlock());
                for (int x = 0; x <= 7; x++) base.add(mc.level.getBlockState(o.offset(x, 1, 0)).getBlock());
                check("mix_applied", base.size() >= 2 && !base.contains(Blocks.AIR), base);
                shot(mc, "mix");
            }
            case 235 -> mc.setScreen(new MixEditorScreen(null, Mixes.byId("stilwerk:castle_wall").orElse(null), mc("spruce"), false, p -> {}));
            case 260 -> shot(mc, "mixeditor");
            case 262 -> {
                mc.setScreen(null);
                PreviewController.cancel();
                ResolvedSelection res = hutBox().resolve(mc.level, 100_000);
                Blueprint bp = Blueprint.capture(mc.level, res, true, mc.level.registryAccess(),
                        new BlueprintMeta("Nordisches Haus", "dev", System.currentTimeMillis(), "stilwerk"));
                try {
                    ClientLibrary.save(bp);
                } catch (java.io.IOException ex) {
                    throw new RuntimeException(ex);
                }
                check("copy_size", bp.nonAir() > 100, bp.nonAir());
                PlacementController.start(bp);
                mc.setScreen(new LibraryScreen(null));
            }
            case 285 -> shot(mc, "library");
            case 287 -> {
                mc.setScreen(null);
                copyAt = o.offset(16, 0, 0);
                // stand back and look down at the ground east of the hut so the ghost follows there
                teleport(server, mc, copyAt.getX() + 4.5, o.getY() + 6, copyAt.getZ() - 14, 0, 22);
            }
            case 315 -> shot(mc, "placement");
            case 317 -> PlacementController.rotate(1);
            case 335 -> shot(mc, "placement_rotated");
            case 337 -> {
                PlacementController.rotate(-1);
                PlacementController.confirm();
            }
            case 380 -> {
                int n = countNonAir(mc, BoundingBox.fromCorners(copyAt.offset(-6, 0, -12), copyAt.offset(14, 10, 14)));
                check("placed_copy", n > 100, n);
                shot(mc, "placed");
                PacketDistributor.sendToServer(new SmallPayloads.UndoRequest());
            }
            case 420 -> {
                int n = countNonAir(mc, BoundingBox.fromCorners(copyAt.offset(-6, 0, -12), copyAt.offset(14, 10, 14)));
                check("undo_removed_copy", n == 0, n);
                PlacementController.stop();
                ModeWheelScreen.holdOpenForTests = true;
                mc.setScreen(new ModeWheelScreen());
            }
            case 422 -> shot(mc, "wheel");
            case 425 -> {
                ModeWheelScreen.holdOpenForTests = false;
                mc.setScreen(new MaterialReportScreen(new SmallPayloads.MaterialReport(99, List.of(
                    new SmallPayloads.Line(Items.SPRUCE_PLANKS, 340, 128, 212, false),
                    new SmallPayloads.Line(Items.SPRUCE_STAIRS, 60, 60, 0, true),
                    new SmallPayloads.Line(Items.DARK_OAK_STAIRS, 44, 0, 44, false)), false)));
            }
            case 445 -> shot(mc, "report");
            case 450 -> mc.setScreen(null);
            // ---------------------------------------------------------------- stage 3: details
            case 460 -> {
                teleport(server, mc, o.getX() + 3.5, o.getY() + 2, o.getZ() - 10, 0, 10);
                ClientState.mode = WandMode.DETAILS;
                Selection.Box b = (Selection.Box) hutBox();
                ClientState.setBox(BoundingBox.fromCorners(b.a(), b.b()));
            }
            case 480 -> com.stilwerk.client.WandClient.openDetails();
            case 500 -> {
                check("details_found", mc.screen instanceof com.stilwerk.client.screen.DetailScreen, mc.screen);
                shot(mc, "details_screen");
            }
            case 505 -> {
                mc.setScreen(null);
                detailFound = com.stilwerk.client.DetailController.findForSelection(hutBox());
                check("details_count", detailFound != null && detailFound.size() >= 5, detailFound == null ? -1 : detailFound.size());
                int window = 0;
                for (int i = 0; i < detailFound.size(); i++) if (detailFound.get(i).rule().equals("window")) window = i;
                for (var s : detailFound) {
                    if (!s.rule().equals("stairs")) continue;
                    LOG.info("[stilwerk dev scene] stairs suggestion at {} state {} first change {}", s.anchor().subtract(o), mc.level.getBlockState(s.anchor()),
                            s.variants().get(0).changes().changes().stream().limit(4).map(c -> c.pos().subtract(o).toShortString()).toList());
                }
                com.stilwerk.client.DetailController.walk(hutBox(), detailFound, window);
            }
            case 525 -> shot(mc, "details_walk_window");
            case 527 -> com.stilwerk.client.DetailController.cycleVariant();
            case 545 -> shot(mc, "details_walk_variant2");
            case 547 -> com.stilwerk.client.DetailController.cycleVariant();
            case 565 -> shot(mc, "details_walk_variant3");
            case 567 -> {
                com.stilwerk.client.DetailController.stop();
                com.stilwerk.client.DetailController.applyAll(hutBox(), detailFound);
            }
            case 610 -> {
                Set<Block> found = blocksInBox(mc, BoundingBox.fromCorners(o.offset(-2, -1, -3), o.offset(10, 9, 9)));
                check("details_applied", found.contains(Blocks.SPRUCE_TRAPDOOR) || found.contains(Blocks.LANTERN), found.size());
                shot(mc, "details_after");
                teleport(server, mc, o.getX() - 6.5, o.getY() + 4, o.getZ() - 7, -45, 20);
            }
            case 630 -> shot(mc, "details_after_side");
            // ---------------------------------------------------------------- stage 4: interior
            case 640 -> {
                ClientState.mode = WandMode.INTERIOR;
                ClientState.clear();
                roomO = o.offset(0, 0, 14);
                server.execute(() -> buildRoom(server.overworld(), roomO));
            }
            case 660 -> {
                teleport(server, mc, roomO.getX() + 1.5, roomO.getY() + 2.4, roomO.getZ() + 1.5, -45, 32);
                com.stilwerk.client.InteriorController.open(roomO.offset(5, -1, 4), Direction.UP);
            }
            case 680 -> {
                check("interior_menu", mc.screen instanceof com.stilwerk.client.screen.InteriorScreen, mc.screen);
                shot(mc, "interior_menu");
                mc.setScreen(null);
                com.stilwerk.client.InteriorController.start("bedroom");
            }
            case 700 -> shot(mc, "interior_bedroom");
            case 702 -> com.stilwerk.client.InteriorController.reroll();
            case 720 -> shot(mc, "interior_bedroom_reroll");
            case 722 -> com.stilwerk.client.InteriorController.nextType(1);
            case 740 -> shot(mc, "interior_living");
            case 742 -> com.stilwerk.client.InteriorController.nextType(1);
            case 760 -> shot(mc, "interior_kitchen");
            case 762 -> com.stilwerk.client.InteriorController.nextType(1);
            case 780 -> shot(mc, "interior_dining");
            case 782 -> com.stilwerk.client.InteriorController.nextType(1);
            case 800 -> shot(mc, "interior_library");
            case 802 -> com.stilwerk.client.InteriorController.nextType(1);
            case 820 -> shot(mc, "interior_bath");
            case 822 -> com.stilwerk.client.InteriorController.nextType(1);
            case 840 -> shot(mc, "interior_workshop");
            case 842 -> {
                com.stilwerk.client.InteriorController.nextType(-6); // back to the bedroom
                for (int i = 0; i < 4 && !com.stilwerk.client.InteriorController.furniture().equals("vanilla"); i++) {
                    com.stilwerk.client.InteriorController.cycleFurniture();
                }
                check("interior_vanilla_choice", com.stilwerk.client.InteriorController.furniture().equals("vanilla"),
                        com.stilwerk.client.InteriorController.furniture());
            }
            case 860 -> shot(mc, "interior_bedroom_vanilla");
            case 862 -> {
                com.stilwerk.client.InteriorController.confirm();
                com.stilwerk.config.ClientConfig.setInteriorFurniture("mods");
            }
            case 880 -> {
                boolean bed = false;
                for (BlockPos p : BlockPos.betweenClosed(roomO.offset(1, 0, 1), roomO.offset(9, 0, 8))) {
                    if (mc.level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) bed = true;
                }
                check("interior_placed_bed", bed, roomO);
                shot(mc, "interior_placed");
            }
            case 890 -> {
                LOG.info("[stilwerk dev scene] DONE with {} failed checks", fails);
                mc.stop();
            }
            default -> {}
        }
    }

    private static List<com.stilwerk.detail.DetailTypes.Suggestion> detailFound;
    private static BlockPos roomO;

    private static Set<Block> blocksInBox(Minecraft mc, BoundingBox bb) {
        Set<Block> out = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
            out.add(mc.level.getBlockState(p).getBlock());
        }
        return out;
    }

    /** A closed spruce room (interior 9 x 4 x 8) with a door north and windows east and south. */
    static void buildRoom(ServerLevel level, BlockPos r) {
        for (int x = 0; x <= 10; x++)
            for (int z = 0; z <= 9; z++) {
                level.setBlock(r.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                for (int y = 0; y <= 4; y++) {
                    boolean wall = x == 0 || z == 0 || x == 10 || z == 9;
                    boolean corner = (x == 0 || x == 10) && (z == 0 || z == 9);
                    BlockState s = y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                            : corner ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : wall ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(r.offset(x, y, z), s, 2);
                }
            }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(r.offset(5, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(r.offset(5, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        for (int y = 1; y <= 2; y++) {
            level.setBlock(r.offset(10, y, 4), Blocks.GLASS.defaultBlockState(), 2);
            level.setBlock(r.offset(10, y, 5), Blocks.GLASS.defaultBlockState(), 2);
            level.setBlock(r.offset(4, y, 9), Blocks.GLASS.defaultBlockState(), 2);
        }
        level.setBlock(r.offset(9, 3, 8), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private static Set<Block> blocksInHut(Minecraft mc) {
        Set<Block> out = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(o, o.offset(7, 7, 6))) out.add(mc.level.getBlockState(p).getBlock());
        return out;
    }

    private static int countNonAir(Minecraft mc, BoundingBox bb) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
            BlockState s = mc.level.getBlockState(p);
            if (!s.isAir() && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.DIRT) && !s.is(Blocks.BEDROCK)) n++;
        }
        return n;
    }

    /** A small Japanese-style hut: stone brick base, acacia walls with log posts, jungle stair roof, cherry trapdoor shutters. */
    static void build(ServerLevel level, BlockPos o) {
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setWeatherParameters(12000, 0, false, false);
        for (int dx = -14; dx <= 36; dx++)
            for (int dz = -14; dz <= 22; dz++) {
                level.setBlockAndUpdate(o.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int dy = 0; dy <= 12; dy++) level.setBlockAndUpdate(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        int w = 8, d = 7;
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                set(level, o.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge) set(level, o.offset(x, 1, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        for (int y = 2; y <= 4; y++)
            for (int x = 0; x < w; x++)
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    if (!edge) continue;
                    boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    set(level, o.offset(x, y, z), corner ? Blocks.ACACIA_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                            : Blocks.ACACIA_PLANKS.defaultBlockState());
                }
        // windows with cherry shutters
        for (int x : new int[]{1, 6}) {
            set(level, o.offset(x, 3, 0), Blocks.GLASS_PANE.defaultBlockState());
            set(level, o.offset(x, 3, -1), Blocks.CHERRY_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH)
                    .setValue(TrapDoorBlock.OPEN, true));
        }
        // door
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(level, o.offset(3, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, o.offset(3, 3, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // roof: jungle stairs on both long sides, slab ridge
        for (int x = -1; x <= w; x++) {
            for (int step = 0; step < 4; step++) {
                set(level, o.offset(x, 5 + step, -1 + step), Blocks.JUNGLE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                set(level, o.offset(x, 5 + step, d - step), Blocks.JUNGLE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
            set(level, o.offset(x, 8, 3), Blocks.JUNGLE_SLAB.defaultBlockState());
        }
        // gable fill
        for (int step = 0; step < 3; step++)
            for (int z = step; z < d - step; z++) {
                set(level, o.offset(0, 5 + step, z), Blocks.ACACIA_PLANKS.defaultBlockState());
                set(level, o.offset(w - 1, 5 + step, z), Blocks.ACACIA_PLANKS.defaultBlockState());
            }
        // porch: fence and lanterns
        for (int x = -1; x <= w; x++) set(level, o.offset(x, 1, -2), Blocks.OAK_FENCE.defaultBlockState());
        set(level, o.offset(2, 4, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, o.offset(5, 4, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, o.offset(2, 5, -1), Blocks.CHAIN.defaultBlockState());
        set(level, o.offset(5, 5, -1), Blocks.CHAIN.defaultBlockState());
        set(level, o.offset(4, 2, 3), Blocks.CHERRY_PLANKS.defaultBlockState());
        set(level, o.offset(-3, 0, 2), Blocks.OAK_LOG.defaultBlockState());
        for (int y = 1; y < 4; y++) set(level, o.offset(-3, y, 2), Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) set(level, o.offset(-3 + dx, 4, 2 + dz), Blocks.OAK_LEAVES.defaultBlockState());
        LOG.info("[stilwerk dev scene] built hut at {}", o);
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }
}
