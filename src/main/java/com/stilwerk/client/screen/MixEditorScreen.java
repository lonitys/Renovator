package com.stilwerk.client.screen;

import com.stilwerk.client.ClientState;
import com.stilwerk.client.Families;
import com.stilwerk.client.PreviewController;
import com.stilwerk.config.JsonStore;
import com.stilwerk.core.Selection;
import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.mix.MixEntry;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.mix.MixPattern;
import com.stilwerk.mix.Mixes;
import com.stilwerk.swap.SwapMapping;
import com.stilwerk.swap.Target;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Builds a mix palette: blocks with weights, a pattern, live preview on the selection. */
public final class MixEditorScreen extends StilwerkScreen {
    private final @Nullable ResourceLocation previewFamily;
    private final boolean includeNature;
    private final Consumer<MixPalette> onSave;
    private String name;
    private final List<MixEntry> entries = new ArrayList<>();
    private MixPattern pattern = MixPattern.SCATTER;
    private Direction.Axis axis = Direction.Axis.Y;
    private float scale = 0.12f;
    private @Nullable ResourceLocation edge;
    private RowList<EntryRow> list;
    private int previewIn = -1;
    private Component status = Component.empty();

    public MixEditorScreen(@Nullable Screen parent, @Nullable MixPalette initial, @Nullable ResourceLocation previewFamily,
                           boolean includeNature, Consumer<MixPalette> onSave) {
        super(Component.translatable("stilwerk.mix.editor"), parent);
        this.previewFamily = previewFamily;
        this.includeNature = includeNature;
        this.onSave = onSave;
        if (initial != null) {
            name = Mixes.nameIsKey(initial) ? Component.translatable(initial.name()).getString() : initial.name();
            entries.addAll(initial.entries());
            pattern = initial.pattern();
            axis = initial.axis();
            scale = initial.scale();
            edge = initial.edge().orElse(null);
        } else {
            name = Component.translatable("stilwerk.mix.default_name").getString();
            if (previewFamily != null) entries.add(new MixEntry(previewFamily, 60));
        }
    }

    @Override
    protected void init() {
        int w = Math.min(360, width - 20), h = height - 20, x = width - w - 10, y = 10;
        panel(x, y, w, h);
        EditBox nameBox = new EditBox(font, x + 8, y + 22, w - 16, 18, Component.translatable("stilwerk.mix.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        addRenderableWidget(CycleButton.<MixPattern>builder(p -> Component.translatable(p.translationKey()))
                .withValues(MixPattern.values()).withInitialValue(pattern)
                .create(x + 8, y + 46, (w - 20) / 2, 20, Component.translatable("stilwerk.mix.pattern"), (b, p) -> {
                    pattern = p;
                    rebuildWidgets();
                    schedulePreview();
                }));
        int ox = x + 12 + (w - 20) / 2, ow = (w - 20) / 2;
        switch (pattern) {
            case GRADIENT -> addRenderableWidget(CycleButton.<Direction.Axis>builder(a -> Component.literal(a.getName().toUpperCase()))
                    .withValues(Direction.Axis.values()).withInitialValue(axis)
                    .create(ox, y + 46, ow, 20, Component.translatable("stilwerk.mix.axis"), (b, a) -> {
                        axis = a;
                        schedulePreview();
                    }));
            case PATCHES -> addRenderableWidget(new Slider(ox, y + 46, ow, Component.translatable("stilwerk.mix.scale"), (scale - 0.03) / 0.47, v -> {
                scale = (float) (0.03 + v * 0.47);
                schedulePreview();
            }, v -> String.format("%.2f", 0.03 + v * 0.47)));
            case EDGES -> addRenderableWidget(Button.builder(edgeLabel(), b -> pickFamily(f -> {
                edge = f;
                schedulePreview();
            })).bounds(ox, y + 46, ow, 20).build());
            default -> {}
        }
        list = new RowList<>(minecraft, x + 4, y + 72, w - 8, h - 72 - 58, 24);
        addRenderableWidget(list);
        List<EntryRow> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) rows.add(new EntryRow(i));
        list.set(rows);
        int by = y + h - 50;
        addRenderableWidget(Button.builder(Component.translatable("stilwerk.mix.add"), b -> pickFamily(f -> {
            entries.add(new MixEntry(f, 20));
            rebuildWidgets();
            schedulePreview();
        })).bounds(x + 8, by, w - 16, 20).build());
        int bw = (w - 20) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(x + 8, by + 24, bw, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 12 + bw, by + 24, bw, 20).build());
        if (previewIn < 0) schedulePreview();
    }

    private Component edgeLabel() {
        Family f = edge == null ? null : FamilyIndex.get().byId(edge);
        return Component.translatable("stilwerk.mix.edge", f == null ? Component.literal("—") : Families.name(f));
    }

    private void pickFamily(Consumer<ResourceLocation> then) {
        minecraft.setScreen(new FamilyPickerScreen(this, Component.translatable("stilwerk.mix.pick"), new FamilyPickerScreen.Options(false, false),
                Map.of(), t -> t.family().ifPresent(then), null));
    }

    private @Nullable MixPalette build(String id) {
        if (entries.isEmpty()) return null;
        return new MixPalette(id, name.isBlank() ? "Mix" : name.trim(), entries, pattern,
                pattern == MixPattern.EDGES ? Optional.ofNullable(edge) : Optional.empty(), axis, scale);
    }

    private void save() {
        MixPalette p = build("user:" + JsonStore.fileName(name.isBlank() ? "mix" : name));
        if (p == null) {
            status = Component.translatable("stilwerk.mix.need_entry");
            return;
        }
        try {
            Mixes.save(p);
        } catch (IOException e) {
            status = Component.translatable("stilwerk.error.save", e.getMessage());
            return;
        }
        PreviewController.cancel();
        minecraft.setScreen(parent);
        onSave.accept(p);
    }

    private void schedulePreview() {
        previewIn = 5; // ticks: debounced so slider drags do not replan every frame
    }

    @Override
    public void tick() {
        if (previewIn > 0 && --previewIn == 0) runPreview();
    }

    private void runPreview() {
        Selection sel = ClientState.selection();
        MixPalette p = build("stilwerk:editing");
        if (sel == null || previewFamily == null || p == null) return;
        Map<String, MixPalette> mx = new LinkedHashMap<>(Mixes.all());
        mx.put(p.id(), p);
        PreviewController.showSwap(sel, SwapMapping.EMPTY.with(previewFamily, Target.mix(p.id())), mx, ClientState.seed,
                includeNature && minecraft.player != null && minecraft.player.isCreative());
    }

    @Override
    public void onClose() {
        PreviewController.cancel();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        int total = entries.stream().mapToInt(MixEntry::weight).sum();
        g.drawString(font, Component.translatable("stilwerk.mix.total", entries.size(), total), panelX + 8, panelY + panelH - 62, 0xFFB8C4CE);
        g.drawString(font, status, panelX + 120, panelY + panelH - 62, 0xFFFF8080);
        if (previewFamily == null) {
            g.drawString(font, Component.translatable("stilwerk.mix.no_preview"), panelX + 8, panelY + 72 - 10, 0xFF9AA8B4);
        }
    }

    // ---------------------------------------------------------------- rows

    private final class EntryRow extends ContainerObjectSelectionList.Entry<EntryRow> {
        final int index;
        final Button family, remove;
        final Slider weight;

        EntryRow(int index) {
            this.index = index;
            MixEntry e = entries.get(index);
            Family f = FamilyIndex.get().byId(e.family());
            family = Button.builder(f == null ? Component.literal(e.family().toString()) : Families.name(f), b -> pickFamily(id -> {
                entries.set(index, new MixEntry(id, entries.get(index).weight()));
                rebuildWidgets();
                schedulePreview();
            })).bounds(0, 0, 130, 20).build();
            weight = new Slider(0, 0, 120, Component.translatable("stilwerk.mix.weight"), (e.weight() - 1) / 99.0, v -> {
                entries.set(index, new MixEntry(entries.get(index).family(), 1 + (int) Math.round(v * 99)));
                schedulePreview();
            }, v -> (1 + (int) Math.round(v * 99)) + "");
            remove = Button.builder(Component.literal("✕"), b -> {
                entries.remove(index);
                rebuildWidgets();
                schedulePreview();
            }).bounds(0, 0, 20, 20).build();
        }

        @Override
        public void render(GuiGraphics g, int i, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            Family f = FamilyIndex.get().byId(entries.get(index).family());
            if (f != null) g.renderItem(Families.icon(f), left + 2, top + 3);
            if (index == 0) g.drawString(font, "★", left + 2, top + 14, 0xFFE8C060);
            family.setPosition(left + 22, top + 1);
            family.setWidth(width - 22 - 124 - 26);
            family.render(g, mx, my, pt);
            weight.setPosition(left + width - 124 - 24, top + 1);
            weight.render(g, mx, my, pt);
            remove.setPosition(left + width - 22, top + 1);
            remove.render(g, mx, my, pt);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(family, weight, remove);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(family, weight, remove);
        }
    }

    /** A 0..1 slider with a label function. */
    static final class Slider extends AbstractSliderButton {
        private final Component prefix;
        private final Consumer<Double> onChange;
        private final java.util.function.DoubleFunction<String> label;

        Slider(int x, int y, int w, Component prefix, double value, Consumer<Double> onChange, java.util.function.DoubleFunction<String> label) {
            super(x, y, w, 20, Component.empty(), Math.max(0, Math.min(1, value)));
            this.prefix = prefix;
            this.onChange = onChange;
            this.label = label;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.empty().append(prefix).append(": " + label.apply(value)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(value);
        }
    }
}
