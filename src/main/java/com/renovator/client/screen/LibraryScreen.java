package com.renovator.client.screen;

import com.renovator.client.ClientLibrary;
import com.renovator.client.PlacementController;
import com.renovator.client.Thumbnails;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The blueprint library: search, thumbnails, place, rename, delete; drop schematic files here to import them. */
public final class LibraryScreen extends RenovatorScreen {
    private List<ClientLibrary.Entry> entries = List.of();
    private @Nullable ClientLibrary.Entry selected;
    private String query = "";
    private Entries list;
    private Button place, rename, delete;
    private Component status = Component.empty();

    public LibraryScreen(@Nullable Screen parent) {
        super(Component.translatable("renovator.library.title"), parent);
    }

    @Override
    protected void init() {
        int w = Math.min(460, width - 20), h = height - 20, x = (width - w) / 2, y = 10;
        panel(x, y, w, h);
        EditBox search = new EditBox(font, x + 8, y + 20, w - 16, 16, Component.translatable("renovator.picker.search"));
        search.setValue(query);
        search.setResponder(v -> {
            query = v;
            refill();
        });
        addRenderableWidget(search);
        list = new Entries(x + 4, y + 40, w - 8, h - 40 - 56);
        addRenderableWidget(list);
        int bw = (w - 16 - 4 * 4) / 5, by = y + h - 50;
        place = addRenderableWidget(Button.builder(Component.translatable("renovator.library.place"), b -> place()).bounds(x + 8, by, bw, 20).build());
        rename = addRenderableWidget(Button.builder(Component.translatable("renovator.library.rename"), b -> rename()).bounds(x + 8 + (bw + 4), by, bw, 20).build());
        delete = addRenderableWidget(Button.builder(Component.translatable("renovator.library.delete"), b -> delete()).bounds(x + 8 + 2 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("renovator.library.folder"), b -> Util.getPlatform().openFile(ClientLibrary.dir().toFile()))
                .bounds(x + 8 + 3 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("renovator.library.refresh"), b -> reload()).bounds(x + 8 + 4 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + w - 108, by + 24, 100, 20).build());
        reload();
    }

    private void reload() {
        entries = ClientLibrary.scan();
        if (selected != null) selected = entries.stream().filter(e -> e.file().equals(selected.file())).findFirst().orElse(null);
        refill();
    }

    private void refill() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Row> rows = new ArrayList<>();
        for (ClientLibrary.Entry e : entries) {
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.file().getFileName().toString().toLowerCase(Locale.ROOT).contains(q)) continue;
            rows.add(new Row(e));
        }
        list.setAll(rows);
        updateButtons();
    }

    private void updateButtons() {
        boolean ok = selected != null && selected.blueprint() != null;
        place.active = ok;
        rename.active = selected != null;
        delete.active = selected != null;
    }

    private void place() {
        if (selected == null || selected.blueprint() == null) return;
        PlacementController.start(selected.blueprint().withMeta(selected.blueprint().meta().withName(selected.name())));
        minecraft.setScreen(null);
    }

    private void rename() {
        if (selected == null) return;
        ClientLibrary.Entry e = selected;
        minecraft.setScreen(new NameInputScreen(this, Component.translatable("renovator.library.rename"), e.name(), name -> {
            try {
                ClientLibrary.rename(e, name);
                selected = null;
            } catch (IOException ex) {
                status = Component.translatable("renovator.error.save", ex.getMessage());
            }
        }));
    }

    private void delete() {
        if (selected == null) return;
        ClientLibrary.Entry e = selected;
        minecraft.setScreen(new ConfirmScreen(this, Component.translatable("renovator.library.delete_confirm", e.name()), () -> {
            try {
                ClientLibrary.delete(e);
                selected = null;
            } catch (IOException ex) {
                status = Component.translatable("renovator.error.save", ex.getMessage());
            }
        }));
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        int n = ClientLibrary.importFiles(files);
        status = Component.translatable("renovator.library.imported", n);
        reload();
    }

    @Override
    public void removed() {
        Thumbnails.clear();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        Component hint = entries.isEmpty() ? Component.translatable("renovator.library.empty") : Component.translatable("renovator.library.drop_hint");
        g.drawString(font, hint, panelX + 8, panelY + panelH - 20, 0xFF9AA8B4);
        g.drawString(font, status, panelX + 8, panelY + panelH - 62, 0xFF9AE0A0);
    }

    private final class Entries extends ObjectSelectionList<Row> {
        Entries(int x, int y, int w, int h) {
            super(LibraryScreen.this.minecraft, w, h, y, 40);
            setX(x);
        }

        void setAll(List<Row> rows) {
            replaceEntries(rows);
            for (Row r : rows) if (selected != null && r.entry.file().equals(selected.file())) setSelected(r);
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
        final ClientLibrary.Entry entry;
        long lastClick;

        Row(ClientLibrary.Entry entry) {
            this.entry = entry;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            if (hovered) g.fill(left, top, left + width, top + height, 0x30FFFFFF);
            if (entry.blueprint() != null) {
                ResourceLocation tex = Thumbnails.get(entry.blueprint());
                g.fill(left + 2, top + 2, left + 38, top + 38, 0xFF202830);
                g.blit(tex, left + 4, top + 4, 0, 0, 32, 32, 32, 32);
            }
            g.drawString(font, font.plainSubstrByWidth(entry.name(), width - 50), left + 44, top + 5, 0xFFFFFFFF);
            String ext = entry.file().getFileName().toString();
            if (entry.blueprint() != null) {
                Vec3i s = entry.blueprint().size();
                g.drawString(font, Component.translatable("renovator.library.info", s.getX(), s.getY(), s.getZ(), entry.blueprint().nonAir(), ext),
                        left + 44, top + 17, 0xFFA8B4BE);
                if (!entry.warnings().isEmpty()) {
                    g.drawString(font, font.plainSubstrByWidth("⚠ " + String.join(", ", entry.warnings()), width - 50), left + 44, top + 28, 0xFFE0B070);
                }
            } else {
                g.drawString(font, Component.translatable("renovator.library.broken", ext), left + 44, top + 17, 0xFFFF7070);
                g.drawString(font, font.plainSubstrByWidth(String.valueOf(entry.error()), width - 50), left + 44, top + 28, 0xFFC06060);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            selected = entry;
            list.setSelected(this);
            updateButtons();
            long now = Util.getMillis();
            if (now - lastClick < 300) place();
            lastClick = now;
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.name());
        }
    }

    /** Yes/no question. */
    private static final class ConfirmScreen extends RenovatorScreen {
        private final Runnable yes;

        ConfirmScreen(Screen parent, Component question, Runnable yes) {
            super(question, parent);
            this.yes = yes;
        }

        @Override
        protected void init() {
            int w = 280, h = 70, x = (width - w) / 2, y = (height - h) / 2;
            panel(x, y, w, h);
            addRenderableWidget(Button.builder(Component.translatable("gui.yes"), b -> {
                minecraft.setScreen(parent);
                yes.run();
            }).bounds(x + 10, y + 40, 125, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.no"), b -> onClose()).bounds(x + w - 135, y + 40, 125, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            g.drawCenteredString(font, title, panelX + panelW / 2, panelY + 14, 0xFFFFFFFF);
        }
    }
}
