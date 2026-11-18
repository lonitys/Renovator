package com.renovator.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Base for Renovator screens: no blur (the world and ghost previews stay visible), never pauses, returns to a parent. */
public abstract class RenovatorScreen extends Screen {
    protected final @Nullable Screen parent;
    protected int panelX, panelY, panelW, panelH;

    protected RenovatorScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Sets the panel rectangle that gets a dark background. */
    protected void panel(int x, int y, int w, int h) {
        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (panelW > 0) {
            g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0101418);
            g.renderOutline(panelX, panelY, panelW, panelH, 0xFF5A6B7A);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Draws the title at the top of the panel. */
    protected void drawTitle(GuiGraphics g) {
        g.drawString(font, title, panelX + 8, panelY + 7, 0xFFE8D9A8);
    }
}
