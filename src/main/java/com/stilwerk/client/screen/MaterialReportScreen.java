package com.stilwerk.client.screen;

import com.stilwerk.net.SmallPayloads.ConfirmRequest;
import com.stilwerk.net.SmallPayloads.Line;
import com.stilwerk.net.SmallPayloads.MaterialReport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Survival: what the operation needs, what the player has, what is missing. Cancel or do as much as possible. */
public final class MaterialReportScreen extends StilwerkScreen {
    private final MaterialReport report;
    private boolean answered;

    public MaterialReportScreen(MaterialReport report) {
        super(Component.translatable("stilwerk.report.title"), null);
        this.report = report;
    }

    @Override
    protected void init() {
        int w = Math.min(340, width - 20), h = Math.min(height - 20, 60 + 20 * Math.max(3, report.lines().size()) + 40);
        int x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        Lines list = new Lines(x + 4, y + 34, w - 8, h - 34 - 32);
        List<Row> rows = new ArrayList<>();
        for (Line l : report.lines()) rows.add(new Row(l));
        list.setAll(rows);
        addRenderableWidget(list);
        int bw = (w - 20) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> answer(false)).bounds(x + 8, y + h - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("stilwerk.report.partial"), b -> answer(true)).bounds(x + 12 + bw, y + h - 26, bw, 20).build());
    }

    private void answer(boolean proceed) {
        answered = true;
        PacketDistributor.sendToServer(new ConfirmRequest(report.id(), proceed));
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        if (!answered) answer(false);
        else super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        g.drawString(font, Component.translatable("stilwerk.report.columns"), panelX + 30, panelY + 22, 0xFF9AA8B4);
    }

    private final class Lines extends ObjectSelectionList<Row> {
        Lines(int x, int y, int w, int h) {
            super(MaterialReportScreen.this.minecraft, w, h, y, 20);
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
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final Line line;

        Row(Line line) {
            this.line = line;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            ItemStack stack = new ItemStack(line.item());
            g.renderItem(stack, left + 2, top + 1);
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), width - 26 - 130);
            g.drawString(font, name, left + 24, top + 6, 0xFFFFFFFF);
            int col = left + width - 128;
            g.drawString(font, String.valueOf(line.need()), col, top + 6, 0xFFE0E0E0);
            g.drawString(font, String.valueOf(line.have()) + (line.emc() ? " +EMC" : ""), col + 36, top + 6, line.emc() ? 0xFFC8A0FF : 0xFFE0E0E0);
            g.drawString(font, String.valueOf(line.missing()), col + 96, top + 6, line.missing() > 0 ? 0xFFFF6060 : 0xFF70E070);
        }

        @Override
        public Component getNarration() {
            return new ItemStack(line.item()).getHoverName();
        }
    }
}
