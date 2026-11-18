package com.renovator.test;

import com.renovator.Renovator;
import com.renovator.core.Applier;
import com.renovator.core.Change;
import com.renovator.core.CreativeGate;
import com.renovator.family.Family;
import com.renovator.family.FamilyIndex;
import com.renovator.interior.InteriorGenerator;
import com.renovator.interior.ModuleLibrary;
import com.renovator.interior.Room;
import com.renovator.interior.RoomDetector;
import com.renovator.interior.RoomType;
import com.renovator.interior.WoodAdapter;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Renovator.MODID)
@PrefixGameTestTemplate(false)
public final class InteriorTests {
    private static final String T = RenovatorGameTests.T;
    static final int Y0 = RenovatorGameTests.FLOOR + 1;

    /** Interior x 2..(1+w), z 2..(1+d), height h; stone brick walls and ceiling; a door in the south wall, glass west. */
    static void room(GameTestHelper h, int w, int d, int height) {
        for (int x = 1; x <= w + 2; x++)
            for (int z = 1; z <= d + 2; z++)
                for (int y = 0; y <= height; y++) {
                    boolean wall = x == 1 || z == 1 || x == w + 2 || z == d + 2;
                    if (wall || y == height) h.setBlock(new BlockPos(x, Y0 + y, z), Blocks.STONE_BRICKS);
                }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        int dx = 2 + w / 2;
        h.setBlock(new BlockPos(dx, Y0, d + 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(dx, Y0 + 1, d + 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        h.setBlock(new BlockPos(1, Y0 + 1, 3), Blocks.GLASS);
    }

    static Room detect(GameTestHelper h) {
        RoomDetector.Result r = RoomDetector.detect(h.getLevel(), h.absolutePos(new BlockPos(3, Y0 - 1, 3)), Direction.UP);
        h.assertTrue(r.room() != null, "room detected: " + r.error() + " at " + r.at());
        return r.room();
    }

    static Family wood(String w) {
        return FamilyIndex.get().byId(ResourceLocation.withDefaultNamespace(w));
    }

    @GameTest(template = T)
    public static void detectsClosedRoom(GameTestHelper h) {
        room(h, 7, 6, 3);
        Room room = detect(h);
        h.assertTrue(room.floor().size() == 42, "7x6 floor, was " + room.floor().size());
        h.assertTrue(room.doors().size() == 1 && room.doorClear().size() == 2, "door with 2 clear cells: " + room.doorClear().size());
        h.assertTrue(room.windowFloor().contains(h.absolutePos(new BlockPos(2, Y0, 3)).asLong()), "window floor under the glass");
        h.assertTrue(room.height(h.absolutePos(new BlockPos(4, Y0, 4)).asLong()) == 3, "height 3");
        h.succeed();
    }

    @GameTest(template = T)
    public static void openRoomIsRejected(GameTestHelper h) {
        room(h, 7, 6, 3);
        h.setBlock(new BlockPos(9, Y0, 4), Blocks.AIR); // hole in the east wall
        RoomDetector.Result r = RoomDetector.detect(h.getLevel(), h.absolutePos(new BlockPos(3, Y0 - 1, 3)), Direction.UP);
        h.assertTrue(r.room() == null && "renovator.interior.open".equals(r.error()), "open room rejected: " + r.error());
        h.succeed();
    }

    static boolean reachable(Room room, java.util.Set<Long> blocked) {
        LongOpenHashSet free = new LongOpenHashSet();
        for (long k : room.floor()) if (!blocked.contains(k)) free.add(k);
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long k : room.doorClear()) if (free.contains(k) && seen.add(k)) q.enqueue(k);
        while (!q.isEmpty()) {
            long c = q.dequeueLong();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                long n = BlockPos.offset(c, d);
                if (free.contains(n) && seen.add(n)) q.enqueue(n);
            }
        }
        return seen.size() == free.size();
    }

    @GameTest(template = T)
    public static void furnishesBedroom(GameTestHelper h) {
        room(h, 7, 6, 3);
        Room room = detect(h);
        RoomType bedroom = ModuleLibrary.type("bedroom");
        InteriorGenerator.Result r = InteriorGenerator.generate(h.getLevel(), room, bedroom, 42, wood("spruce"));
        h.assertTrue(r.placed().stream().anyMatch(id -> id.startsWith("bed")), "a bed group placed: " + r.placed());
        h.assertTrue(r.placed().stream().anyMatch(id -> id.startsWith("wardrobe")), "a wardrobe placed: " + r.placed());
        java.util.Set<Long> blocked = new java.util.HashSet<>(r.footprints());
        boolean spruce = false;
        for (Change c : r.changes().changes()) {
            h.assertTrue(c.oldState().isAir(), "only into air: " + c.pos() + " " + c.oldState());
            h.assertFalse(room.doorClear().contains(c.pos().asLong()) && !c.newState().is(net.minecraft.tags.BlockTags.WOOL_CARPETS),
                    "door clearance kept free: " + c.newState());
            if (c.pos().getY() == room.floorY() && !c.newState().is(net.minecraft.tags.BlockTags.WOOL_CARPETS)) blocked.add(c.pos().asLong());
            if (c.newState().getBlock() instanceof StairBlock && c.newState().is(Blocks.SPRUCE_STAIRS)) spruce = true;
        }
        h.assertTrue(reachable(room, blocked), "every free floor cell reachable from the door");
        InteriorGenerator.Result again = InteriorGenerator.generate(h.getLevel(), room, bedroom, 42, wood("spruce"));
        h.assertTrue(again.changes().equals(r.changes()), "deterministic per seed");
        Applier.applyNow(h.getLevel(), r.changes(), CreativeGate.INSTANCE);
        h.succeed();
    }

    @GameTest(template = T)
    public static void everyTypeFurnishes(GameTestHelper h) {
        room(h, 9, 9, 4);
        Room room = detect(h);
        for (RoomType t : ModuleLibrary.types()) {
            InteriorGenerator.Result r = InteriorGenerator.generate(h.getLevel(), room, t, 7, wood("oak"));
            long modules = r.placed().stream().filter(id -> !id.equals("rug") && !id.equals("light")).count();
            h.assertTrue(modules >= 1, t.id() + " placed something: " + r.placed());
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void woodAndColour(GameTestHelper h) {
        BlockState oakStairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState spruce = WoodAdapter.adapt(oakStairs, wood("spruce"));
        h.assertTrue(spruce.is(Blocks.SPRUCE_STAIRS) && spruce.getValue(StairBlock.FACING) == Direction.EAST, "oak stairs → spruce: " + spruce);
        h.assertTrue(WoodAdapter.adapt(Blocks.STONE.defaultBlockState(), wood("spruce")).is(Blocks.STONE), "stone unchanged");
        h.assertTrue(WoodAdapter.adapt(Blocks.OAK_PLANKS.defaultBlockState(), wood("dark_oak")).is(Blocks.DARK_OAK_PLANKS), "oak → dark oak");
        for (String c : ModuleLibrary.categories()) h.assertTrue(!ModuleLibrary.available(c).isEmpty(), "category " + c + " has modules");
        if (!net.neoforged.fml.ModList.get().isLoaded("mcwfurnitures") && !net.neoforged.fml.ModList.get().isLoaded("refurbished_furniture")) {
            h.assertTrue(ModuleLibrary.available("bed").stream().noneMatch(m -> m.modded()), "no modded beds without the mods");
        }
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void serverFurnishes(GameTestHelper h) {
        room(h, 7, 6, 3);
        net.neoforged.neoforge.common.util.FakePlayer p = TestPlayers.player(h, net.minecraft.world.level.GameType.CREATIVE);
        com.renovator.net.ServerOps.interior(p, new com.renovator.net.InteriorRequest(1, h.absolutePos(new BlockPos(3, Y0 - 1, 3)),
                Direction.UP, "bedroom", 99, "mods", false));
        h.succeedWhen(() -> {
            boolean bed = false;
            for (int x = 2; x <= 8; x++)
                for (int z = 2; z <= 7; z++) if (h.getBlockState(new BlockPos(x, Y0, z)).getBlock() instanceof net.minecraft.world.level.block.BedBlock) bed = true;
            h.assertTrue(bed, "a bed was placed by the server");
        });
    }

    @GameTest(template = T)
    public static void autoType(GameTestHelper h) {
        room(h, 7, 6, 3);
        h.setBlock(new BlockPos(9, Y0, 5), Blocks.FURNACE); // in the east wall
        h.assertTrue(InteriorGenerator.auto(h.getLevel(), detect(h)).id().equals("workshop"), "furnace → workshop");
        h.succeed();
    }
}
