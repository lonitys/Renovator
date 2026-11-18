package com.stilwerk.client.screen;

import com.stilwerk.client.DetailController;
import com.stilwerk.core.Selection;
import com.stilwerk.detail.DetailTypes.Suggestion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/** The detail checklist: suggestions per rule, walk through them in the world, or apply all. */
public final class DetailScreen extends StilwerkScreen {
    private final Selection selection;
    private final List<Suggestion> found;

    public DetailScreen(Selection selection, List<Suggestion> found) {
        super(Component.translatable("stilwerk.detail.title"), null);
        this.selection = selection;
        this.found = found;
    }

    @Override
    protected void init() {
        int w = Math.min(320, width - 20), h = Math.min(height - 20, 90 + 22 * 8), x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        Rows list = new Rows(x + 4, y + 36, w - 8, h - 36 - 56);
        Map<String, List<Integer>> byRule = new LinkedHashMap<>();
        for (int i = 0; i < found.size(); i++) byRule.computeIfAbsent(found.get(i).rule(), k -> new ArrayList<>()).add(i);
        List<Row> rows = new ArrayList<>();
        byRule.forEach((rule, idx) -> rows.add(new Row(rule, idx.size(), idx.get(0))));
        list.setAll(rows);
        addRenderableWidget(list);
        int bw = (w - 20) / 2;
        Button walk = Button.builder(Component.translatable("stilwerk.detail.walk"), b -> start(0)).bounds(x + 8, y + h - 50, bw, 20).build();
        Button all = Button.builder(Component.translatable("stilwerk.detail.apply_all"), b -> {
            DetailController.applyAll(selection, found);
            onClose();
        }).bounds(x + 12 + bw, y + h - 50, bw, 20).build();
        walk.active = all.active = !found.isEmpty();
        addRenderableWidget(walk);
        addRenderableWidget(all);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 8, y + h - 26, w - 16, 20).build());
    }

    private void start(int index) {
        minecraft.setScreen(null);
        DetailController.walk(selection, found, index);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        Component summary = found.isEmpty() ? Component.translatable("stilwerk.detail.none")
                : Component.translatable("stilwerk.detail.count", found.size());
        g.drawString(font, summary, panelX + 8, panelY + 20, found.isEmpty() ? 0xFFB0B0B0 : 0xFF9AE0A0);
    }

    private final class Rows extends ObjectSelectionList<Row> {
        Rows(int x, int y, int w, int h) {
            super(DetailScreen.this.minecraft, w, h, y, 22);
            setX(x);
        }

        void setAll(List<Row> rows) {
            replaceEntries(rows);
        }

        @Override
        public int getRowWidth() {
            return width - 14;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + width - 6;
        }

        @Override
        protected void renderListBackground(GuiGraphics g) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics g) {
            g.fill(getX(), getY() - 1, getRight(), getY(), 0x605A6B7A);
            g.fill(getX(), getBottom(), getRight(), getBottom() + 1, 0x605A6B7A);
        }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final String rule;
        final int count, first;

        Row(String rule, int count, int first) {
            this.rule = rule;
            this.count = count;
            this.first = first;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            if (hovered) g.fill(left, top, left + width, top + height, 0x30FFFFFF);
            g.drawString(font, Component.translatable("stilwerk.detail." + rule), left + 6, top + 7, 0xFFFFFFFF);
            String n = "×" + count;
            g.drawString(font, n, left + width - 8 - font.width(n), top + 7, 0xFFB0B0B0);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            start(first);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("stilwerk.detail." + rule);
        }
    }
}
