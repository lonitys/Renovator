package com.renovator.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.renovator.Renovator;
import com.renovator.blueprint.Blueprint;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Top-down map-colour images of blueprints (64x64, letterboxed), one texture per blueprint. */
public final class Thumbnails {
    private Thumbnails() {}

    public static final int SIZE = 64;
    private static final Map<Blueprint, ResourceLocation> CACHE = new HashMap<>();
    private static int counter;

    public static ResourceLocation get(Blueprint bp) {
        ResourceLocation id = CACHE.get(bp);
        if (id != null) return id;
        NativeImage img = render(bp);
        id = Renovator.id("thumb/" + counter++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        CACHE.put(bp, id);
        return id;
    }

    /** Frees all thumbnails (library closed). */
    public static void clear() {
        for (ResourceLocation id : CACHE.values()) Minecraft.getInstance().getTextureManager().release(id);
        CACHE.clear();
    }

    static NativeImage render(Blueprint bp) {
        NativeImage img = new NativeImage(SIZE, SIZE, true);
        img.fillRect(0, 0, SIZE, SIZE, 0);
        Vec3i s = bp.size();
        double f = SIZE / (double) Math.max(s.getX(), s.getZ()); // pixels per block
        int w = Math.max(1, (int) Math.round(s.getX() * f)), d = Math.max(1, (int) Math.round(s.getZ() * f));
        int offX = (SIZE - w) / 2, offZ = (SIZE - d) / 2;
        for (int px = offX; px < offX + w; px++) {
            for (int pz = offZ; pz < offZ + d; pz++) {
                int x = Math.min(s.getX() - 1, (int) ((px - offX) / f)), z = Math.min(s.getZ() - 1, (int) ((pz - offZ) / f));
                for (int y = s.getY() - 1; y >= 0; y--) {
                    BlockState st = bp.state(x, y, z);
                    if (st == null || st.isAir()) continue;
                    MapColor c = st.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    if (c == MapColor.NONE) continue;
                    MapColor.Brightness b = y > s.getY() * 2 / 3 ? MapColor.Brightness.HIGH : y < s.getY() / 3 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    img.setPixelRGBA(px, pz, c.calculateRGBColor(b));
                    break;
                }
            }
        }
        return img;
    }
}
