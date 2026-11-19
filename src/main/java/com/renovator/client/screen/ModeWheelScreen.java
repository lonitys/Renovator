package com.renovator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.renovator.client.ClientState;
import com.renovator.client.Keys;
import com.renovator.client.WandClient;
import com.renovator.item.WandMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Hold the mode key, point at a mode, release (or click) to choose. */
public final class ModeWheelScreen extends RenovatorScreen {
    private static final int RADIUS = 74, TILE = 54;
    /** Tile angles: evenly around the circle, the first mode at the top. */
    private static final double[] ANGLES = angles(WandMode.values().length);

    private static double[] angles(int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = -90 + 360.0 * i / n;
        return a;
    }
    /** Dev scene: keep the wheel open without the key held. */
    public static boolean holdOpenForTests;

    public ModeWheelScreen() {
        super(Component.translatable("renovator.wheel.title"), null);
    }

    private int tileX(int i) {
        return width / 2 + (int) Math.round(Math.cos(Math.toRadians(ANGLES[i])) * RADIUS) - TILE / 2;
    }

    private int tileY(int i) {
        return height / 2 + (int) Math.round(Math.sin(Math.toRadians(ANGLES[i])) * RADIUS) - TILE / 2;
    }

    private @Nullable WandMode hovered(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 15 * 15) return null;
        double a = Math.toDegrees(Math.atan2(dy, dx));
        int best = 0;
        double bestDiff = 999;
        for (int i = 0; i < ANGLES.length; i++) {
            double diff = Math.abs(((a - ANGLES[i]) % 360 + 540) % 360 - 180);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return WandMode.values()[best];
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        WandMode hover = hovered(mouseX, mouseY);
        g.drawCenteredString(font, title, width / 2, height / 2 - RADIUS - TILE / 2 - 16, 0xFFE8D9A8);
        for (int i = 0; i < ANGLES.length; i++) {
            WandMode m = WandMode.values()[i];
            int x = tileX(i), y = tileY(i);
            boolean current = m == ClientState.mode, hot = m == hover;
            g.fill(x, y, x + TILE, y + TILE, hot ? 0xE0384A5C : 0xC0141A20);
            g.renderOutline(x, y, TILE, TILE, current ? 0xFFE8C060 : hot ? 0xFFB0D0F0 : 0xFF5A6B7A);
            g.pose().pushPose();
            g.pose().translate(x + TILE / 2f - 12, y + 8, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(new ItemStack(m.icon()), 0, 0);
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable(m.translationKey()), x + TILE / 2, y + TILE - 12, hot ? 0xFFFFFFFF : 0xFFC8C8C8);
        }
        g.drawCenteredString(font, Component.translatable("renovator.wheel.hint"), width / 2, height / 2 + RADIUS + TILE / 2 + 8, 0xFF9AA8B4);
    }

    @Override
    public void tick() {
        if (holdOpenForTests) return;
        long window = minecraft.getWindow().getWindow();
        InputConstants.Key key = Keys.MODE_WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(window, key.getValue())) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            choose(hovered(mx, my));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        choose(hovered(mx, my));
        return true;
    }

    private void choose(@Nullable WandMode m) {
        if (m != null) WandClient.setMode(m);
        onClose();
    }
}
