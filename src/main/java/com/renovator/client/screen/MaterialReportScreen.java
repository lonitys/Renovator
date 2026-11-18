package com.renovator.client.screen;

import com.renovator.net.SmallPayloads.ConfirmAction;
import com.renovator.net.SmallPayloads.ConfirmRequest;
import com.renovator.net.SmallPayloads.Line;
import com.renovator.net.SmallPayloads.MaterialReport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Survival build dialog: what the operation needs, what the player has, what is missing. Build (everything, or as
 * far as it goes), keep it as a projection to build by hand / layer by layer, or cancel.
 */
public final class MaterialReportScreen extends RenovatorScreen {
    private final MaterialReport report;
    private boolean answered;

    public MaterialReportScreen(MaterialReport report) {
        super(Component.translatable(report.complete() ? "renovator.report.title_ready" : "renovator.report.title"), null);
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
        int bw = (w - 24) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> answer(ConfirmAction.CANCEL))
                .bounds(x + 8, y + h - 26, bw, 20).build());
        Button project = Button.builder(Component.translatable("renovator.report.project"), b -> answer(ConfirmAction.PROJECT))
                .bounds(x + 12 + bw, y + h - 26, bw, 20).build();
        project.setTooltip(Tooltip.create(Component.translatable("renovator.report.project_tip")));
        addRenderableWidget(project);
        addRenderableWidget(Button.builder(Component.translatable(report.complete() ? "renovator.report.build" : "renovator.report.partial"),
                b -> answer(ConfirmAction.BUILD)).bounds(x + 16 + 2 * bw, y + h - 26, bw, 20).build());
    }

    private void answer(ConfirmAction action) {
        answered = true;
        PacketDistributor.sendToServer(new ConfirmRequest(report.id(), action));
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        if (!answered) answer(ConfirmAction.CANCEL);
        else super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        // same geometry as the rows: list at panelX + 4, row width = list width - 14, centred
        int listW = panelW - 8, rowW = listW - 14, left = panelX + 4 + (listW - rowW) / 2, col = left + rowW - 150;
        g.drawString(font, Component.translatable("renovator.report.col_block"), left + 24, panelY + 22, 0xFF9AA8B4);
        g.drawString(font, Component.translatable("renovator.report.col_need"), col, panelY + 22, 0xFF9AA8B4);
        g.drawString(font, Component.translatable("renovator.report.col_have"), col + 52, panelY + 22, 0xFF9AA8B4);
        g.drawString(font, Component.translatable("renovator.report.col_missing"), col + 112, panelY + 22, 0xFF9AA8B4);
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
        final Line line;

        Row(Line line) {
            this.line = line;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            ItemStack stack = new ItemStack(line.item());
            g.renderItem(stack, left + 2, top + 1);
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), width - 26 - 154);
            g.drawString(font, name, left + 24, top + 6, 0xFFFFFFFF);
            int col = left + width - 150;
            g.drawString(font, String.valueOf(line.need()), col, top + 6, 0xFFE0E0E0);
            g.drawString(font, String.valueOf(line.have()) + (line.emc() ? " +EMC" : ""), col + 52, top + 6, line.emc() ? 0xFFC8A0FF : 0xFFE0E0E0);
            g.drawString(font, String.valueOf(line.missing()), col + 112, top + 6, line.missing() > 0 ? 0xFFFF6060 : 0xFF70E070);
        }

        @Override
        public Component getNarration() {
            return new ItemStack(line.item()).getHoverName();
        }
    }
}
