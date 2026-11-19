package com.renovator.client.screen;

import com.renovator.client.projection.ClientProjection;
import com.renovator.client.projection.ClientProjections;
import com.renovator.net.ProjectionPayloads.Action;
import com.renovator.net.ProjectionPayloads.Build;
import com.renovator.net.ProjectionPayloads.ProjAction;
import com.renovator.net.ProjectionPayloads.Scope;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The player's projections: progress, show/hide, focus, build a layer / up to it / everything, delete; style and view. */
public final class ProjectionScreen extends RenovatorScreen {
    private static final int ROW = 26;

    public ProjectionScreen() {
        super(Component.translatable("renovator.projection.title"), null);
    }

    @Override
    protected void init() {
        List<ClientProjection> list = ClientProjections.all();
        int w = Math.min(width - 20, 460), rows = Math.max(1, list.size());
        int h = 30 + rows * ROW + 36, x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        String here = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        for (int r = 0; r < list.size(); r++) {
            ClientProjection cp = list.get(r);
            boolean sameDim = cp.dimension.toString().equals(here);
            boolean focused = ClientProjections.focusedProjection() == cp;
            int by = y + 26 + r * ROW + 2, bx = x + w - 8;
            bx -= 20;
            add(bx, by, 20, Component.literal("✕"), "renovator.projection.delete", true, () -> {
                PacketDistributor.sendToServer(new ProjAction(cp.id, Action.DELETE));
                ClientProjections.removed(cp.id);
            });
            bx -= 46;
            add(bx, by, 44, Component.translatable("renovator.projection.build_all"), "renovator.projection.build_all_tip", sameDim,
                    () -> build(cp, Scope.ALL));
            bx -= 56;
            add(bx, by, 54, Component.translatable("renovator.projection.build_up_to"), "renovator.projection.build_up_to_tip", sameDim,
                    () -> build(cp, Scope.UP_TO));
            bx -= 56;
            add(bx, by, 54, Component.translatable("renovator.projection.build_layer"), "renovator.projection.build_layer_tip", sameDim,
                    () -> build(cp, Scope.LAYER));
            bx -= 22;
            add(bx, by, 20, Component.literal(focused ? "◉" : "◎"), "renovator.projection.focus", sameDim, () -> ClientProjections.focus(cp.id));
            bx -= 22;
            add(bx, by, 20, Component.literal(ClientProjections.hidden(cp.id) ? "○" : "●"),
                    ClientProjections.hidden(cp.id) ? "renovator.projection.show" : "renovator.projection.hide", true,
                    () -> ClientProjections.toggleHidden(cp.id));
        }
        int bw = (w - 24) / 3, by = y + h - 28;
        addRenderableWidget(Button.builder(styleLabel(), b -> {
            ClientProjections.cycleStyle();
            b.setMessage(styleLabel());
        }).bounds(x + 8, by, bw, 20).build());
        addRenderableWidget(Button.builder(viewLabel(), b -> {
            ClientProjections.cycleView();
            b.setMessage(viewLabel());
        }).bounds(x + 12 + bw, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 16 + 2 * bw, by, bw, 20).build());
    }

    private static Component styleLabel() {
        return Component.translatable("renovator.projection.style." + ClientProjections.style().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component viewLabel() {
        return Component.translatable("renovator.projection.view." + ClientProjections.view().name().toLowerCase(java.util.Locale.ROOT));
    }

    private void add(int x, int y, int w, Component label, String tip, boolean active, Runnable action) {
        Button b = Button.builder(label, btn -> {
            action.run();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build();
        b.setTooltip(Tooltip.create(Component.translatable(tip)));
        b.active = active;
        addRenderableWidget(b);
    }

    private void build(ClientProjection cp, Scope scope) {
        ClientProjections.focus(cp.id);
        PacketDistributor.sendToServer(new Build(cp.id, scope, ClientProjections.layerY()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        List<ClientProjection> list = ClientProjections.all();
        if (list.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("renovator.projection.empty"), panelX + 8, panelY + 30, panelW - 16, 0xFF9AA8B4);
            return;
        }
        String here = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        int textW = panelW - 16 - 240;
        for (int r = 0; r < list.size(); r++) {
            ClientProjection cp = list.get(r);
            int ty = panelY + 26 + r * ROW + 2;
            boolean sameDim = cp.dimension.toString().equals(here);
            String name = font.plainSubstrByWidth(cp.label().getString(), textW);
            g.drawString(font, name, panelX + 8, ty + 1, sameDim ? 0xFFFFFFFF : 0xFF8090A0);
            int pct = cp.size() == 0 ? 100 : cp.done() * 100 / cp.size();
            Component progress = sameDim ? Component.translatable("renovator.projection.progress", cp.done(), cp.size(), pct)
                    : Component.translatable("renovator.projection.other_dimension");
            g.drawString(font, progress, panelX + 8, ty + 11, sameDim ? 0xFF9AE0A0 : 0xFF8090A0);
        }
    }
}
