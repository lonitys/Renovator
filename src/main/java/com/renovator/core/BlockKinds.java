package com.renovator.core;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sorts built blocks into the body of a building ({@link #CORE}: walls, roof, floors under the roof) and the
 * {@link #LOOSE} things that hang on it or lie around it: slim blocks (fences, walls, panes, lanterns, torches,
 * trapdoors, ladders ...) and thin layers on the terrain under open sky (roads, path slabs, low garden walls).
 * Only core blocks carry the building flood; loose groups join a building when they hug it.
 * Chunks that are not loaded read as air, so a detection never loads or generates terrain.
 * One instance per detection run (results are cached per position).
 */
public final class BlockKinds {
    public static final byte NONE = 0, CORE = 1, LOOSE = 2;
    /** A stack of at most this many blocks resting on terrain under open sky is a ground layer. */
    static final int GROUND_HEIGHT = 2;
    /** How far above a ground layer something built makes it "covered" (a floor under a roof). */
    static final int COVER_SCAN = 24;

    private static final Map<Block, Boolean> SLIM = new ConcurrentHashMap<>();

    private final BlockGetter level;
    private final @Nullable LevelReader reader;
    private final NatureFilter nature;
    private final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();
    private final Long2BooleanOpenHashMap loadedChunks = new Long2BooleanOpenHashMap();
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    public BlockKinds(BlockGetter level) {
        this.level = level;
        this.reader = level instanceof LevelReader r ? r : null;
        this.nature = new NatureFilter(level);
        cache.defaultReturnValue((byte) -1);
    }

    public byte kind(BlockPos p) {
        return kind(p.getX(), p.getY(), p.getZ());
    }

    public byte kind(long key) {
        return kind(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }

    public byte kind(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        byte c = cache.get(k);
        if (c >= 0) return c;
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = state(p);
        if (invisible(s) || nature.isNatural(p, s)) {
            cache.put(k, NONE);
            return NONE;
        }
        if (slim(s)) {
            cache.put(k, LOOSE);
            return LOOSE;
        }
        classifyStack(x, y, z);
        return cache.get(k);
    }

    /** Built and not slim: a block that can carry weight in a wall or a road. */
    private boolean solid(int x, int y, int z) {
        m.set(x, y, z);
        BlockState s = state(m);
        return !invisible(s) && !slim(s) && !nature.isNatural(m.immutable(), s);
    }

    /** The block, or air in chunks that are not loaded (never loads or generates a chunk). */
    private BlockState state(BlockPos p) {
        if (reader != null) {
            long chunk = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            if (!loadedChunks.computeIfAbsent(chunk, c -> reader.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c)))) return Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(p);
    }

    /** Air and technical blocks nobody sees (barrier, light, structure void): never part of a building. */
    static boolean invisible(BlockState s) {
        return s.isAir() || s.getRenderShape() == RenderShape.INVISIBLE;
    }

    /** Classifies the vertical run of solid blocks through (x, y, z): a thin layer on terrain under open sky is loose. */
    private void classifyStack(int x, int y, int z) {
        int y0 = y, y1 = y;
        while (y0 > y - 3 && solid(x, y0 - 1, z)) y0--;
        while (y1 < y + 3 && solid(x, y1 + 1, z)) y1++;
        byte r = CORE;
        if (y1 - y0 + 1 <= GROUND_HEIGHT) {
            m.set(x, y0 - 1, z);
            BlockState below = state(m);
            if (!below.isAir() && nature.isNatural(m.immutable(), below)) {
                boolean covered = false;
                int top = Math.min(y1 + COVER_SCAN, level.getMaxBuildHeight() - 1);
                for (int yy = y1 + 1; yy <= top && !covered; yy++) covered = solid(x, yy, z);
                if (!covered) r = LOOSE;
            }
        }
        for (int yy = y0; yy <= y1; yy++) cache.put(BlockPos.asLong(x, yy, z), r);
    }

    /** Slim or flat blocks that decorate or connect rather than form a wall. Cached per block. */
    public static boolean slim(BlockState s) {
        return SLIM.computeIfAbsent(s.getBlock(), BlockKinds::computeSlim);
    }

    private static boolean computeSlim(Block b) {
        if (b instanceof FenceBlock || b instanceof FenceGateBlock || b instanceof WallBlock || b instanceof IronBarsBlock
                || b instanceof ChainBlock || b instanceof BaseRailBlock || b instanceof CarpetBlock || b instanceof LanternBlock
                || b instanceof TorchBlock || b instanceof TrapDoorBlock || b instanceof SignBlock || b instanceof AbstractBannerBlock
                || b instanceof FlowerPotBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BasePressurePlateBlock
                || b instanceof RedStoneWireBlock || b instanceof CandleBlock || b instanceof LadderBlock || b instanceof RodBlock
                || b instanceof ScaffoldingBlock) {
            return true;
        }
        try {
            VoxelShape shape = b.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) return true;
            AABB a = shape.bounds();
            return (a.getXsize() < 0.75 && a.getZsize() < 0.75) || a.getYsize() <= 3 / 16.0 + 1e-6;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
