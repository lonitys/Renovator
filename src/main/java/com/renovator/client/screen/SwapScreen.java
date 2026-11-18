package com.renovator.client.screen;

import com.renovator.blueprint.Blueprint;
import com.renovator.client.ClientState;
import com.renovator.client.Families;
import com.renovator.client.PlacementController;
import com.renovator.client.PreviewController;
import com.renovator.config.JsonStore;
import com.renovator.core.Selection;
import com.renovator.family.Shape;
import com.renovator.mix.MixPalette;
import com.renovator.mix.Mixes;
import com.renovator.swap.SwapMapping;
import com.renovator.swap.SwapPlanner;
import com.renovator.swap.Target;
import com.renovator.swap.Template;
import com.renovator.swap.Templates;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Style swap: every family in the selection (or blueprint) with its target; templates; preview / apply. */
public final class SwapScreen extends RenovatorScreen {
    private final boolean blueprintMode;
    private List<SwapPlanner.Census> census = List.of();
    private SwapMapping mapping;
    private Map<String, MixPalette> mixes;
    private final Set<ResourceLocation> expanded = new HashSet<>();
    private boolean includeNature;
    private Optional<Template> template = Optional.empty();
    private RowList<Row> list;
    private Component note = Component.empty();

    private SwapScreen(boolean blueprintMode, SwapMapping mapping) {
        super(Component.translatable(blueprintMode ? "renovator.swap.title_blueprint" : "renovator.swap.title"), null);
        this.blueprintMode = blueprintMode;
        this.mapping = mapping;
        this.mixes = new LinkedHashMap<>(Mixes.all());
        if (blueprintMode) mixes.putAll(PlacementController.mixes());
        this.includeNature = ClientState.includeNature;
        recensus();
    }

    public static SwapScreen forSelection() {
        return new SwapScreen(false, ClientState.mapping);
    }

    public static SwapScreen forBlueprint() {
        return new SwapScreen(true, PlacementController.mapping());
    }

    private boolean creative() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
    }

    private void recensus() {
        Minecraft mc = Minecraft.getInstance();
        if (blueprintMode) {
            Blueprint bp = PlacementController.transformed() != null ? PlacementController.transformed() : PlacementController.blueprint();
            census = bp == null ? List.of() : SwapPlanner.censusStates(bp.states());
        } else {
            Selection sel = ClientState.selection();
            if (sel == null || mc.level == null) {
                census = List.of();
                return;
            }
            var res = sel.resolve(mc.level, ClientState.CLIENT_LIMIT);
            census = res.truncated() ? List.of() : SwapPlanner.census(mc.level, res, includeNature && creative());
            if (res.truncated()) note = Component.translatable("renovator.select.too_big");
        }
    }

    private List<ResourceLocation> present() {
        List<ResourceLocation> out = new ArrayList<>();
        for (SwapPlanner.Census c : census) out.add(c.family().id());
        return out;
    }

    @Override
    protected void init() {
        int w = Math.min(430, width - 20), h = height - 20, x = 10, y = 10;
        panel(x, y, w, h);
        int top = y + 22;
        List<Optional<Template>> templates = new ArrayList<>();
        templates.add(Optional.empty());
        for (Template t : Templates.all()) templates.add(Optional.of(t));
        addRenderableWidget(CycleButton.<Optional<Template>>builder(t -> t.map(Families::name).orElse(Component.translatable("renovator.swap.no_template")))
                .withValues(templates).withInitialValue(template)
                .create(x + 8, top, w - 16 - (blueprintMode || !creative() ? 0 : 130), 20, Component.translatable("renovator.swap.template"),
                        (b, t) -> {
                            template = t;
                            t.ifPresent(tp -> mapping = tp.applyTo(mapping, present()));
                            rebuildRows();
                        }));
        if (!blueprintMode && creative()) {
            addRenderableWidget(Checkbox.builder(Component.translatable("renovator.swap.include_nature"), font)
                    .pos(x + w - 126, top + 2).selected(includeNature).onValueChange((cb, v) -> {
                        includeNature = v;
                        recensus();
                        rebuildRows();
                    }).build());
        }
        list = new RowList<>(minecraft, x + 4, top + 26, w - 8, h - 26 - 26 - 62, 22);
        addRenderableWidget(list);
        int by = y + h - 26, bw = (w - 16 - 3 * 4) / 4;
        addRenderableWidget(Button.builder(Component.translatable("renovator.swap.preview"), b -> preview()).bounds(x + 8, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("renovator.swap.apply"), b -> apply()).bounds(x + 8 + (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("renovator.swap.save_template"), b -> saveTemplate())
                .bounds(x + 8 + 2 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 8 + 3 * (bw + 4), by, bw, 20).build());
        rebuildRows();
    }

    private void rebuildRows() {
        if (list == null) return;
        List<Row> rows = new ArrayList<>();
        for (SwapPlanner.Census c : census) {
            rows.add(new FamilyRow(c));
            if (expanded.contains(c.family().id())) {
                for (Map.Entry<Shape, Integer> e : c.shapes().entrySet()) rows.add(new ShapeRow(c, e.getKey(), e.getValue()));
            }
        }
        list.set(rows);
        ClientState.mapping = blueprintMode ? ClientState.mapping : mapping;
    }

    private void openPicker(Component title, Consumer<Target> onPick, @Nullable ResourceLocation forFamily) {
        Consumer<Consumer<MixPalette>> newMix = cb -> minecraft.setScreen(new MixEditorScreen(minecraft.screen, null,
                blueprintMode ? null : forFamily, includeNature, saved -> {
                    mixes.put(saved.id(), saved);
                    cb.accept(saved);
                }));
        minecraft.setScreen(new FamilyPickerScreen(this, title, new FamilyPickerScreen.Options(true, true), mixes, onPick, newMix));
    }

    private void preview() {
        if (blueprintMode) {
            PlacementController.setMapping(mapping, mixes);
            minecraft.setScreen(null);
            return;
        }
        Selection sel = ClientState.selection();
        if (sel == null) return;
        ClientState.includeNature = includeNature;
        minecraft.setScreen(null);
        PreviewController.showSwap(sel, mapping, mixes, ClientState.seed, includeNature && creative());
    }

    private void apply() {
        if (blueprintMode) {
            PlacementController.setMapping(mapping, mixes);
            minecraft.setScreen(null);
            PlacementController.confirm();
            return;
        }
        Selection sel = ClientState.selection();
        if (sel == null) return;
        ClientState.includeNature = includeNature;
        minecraft.setScreen(null);
        PreviewController.applyDirect(sel, mapping, mixes, ClientState.seed, includeNature && creative());
    }

    private void saveTemplate() {
        minecraft.setScreen(new NameInputScreen(this, Component.translatable("renovator.swap.template_name"), "", name -> {
            Template t = new Template("user:" + JsonStore.fileName(name), name, mapping.families());
            try {
                Templates.save(t);
                template = Optional.of(t);
                note = Component.translatable("renovator.swap.template_saved", name);
            } catch (IOException e) {
                note = Component.translatable("renovator.error.save", e.getMessage());
            }
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        int infoY = panelY + panelH - 26 - 58;
        int total = 0;
        for (SwapPlanner.Census c : census) total += c.count();
        g.drawString(font, Component.translatable("renovator.swap.summary", census.size(), total), panelX + 8, infoY, 0xFFB8C4CE);
        List<SwapPlanner.Kept> kept = blueprintMode ? List.of() : PreviewController.lastKept();
        int ly = infoY + 12;
        for (int i = 0; i < Math.min(2, kept.size()); i++, ly += 11) {
            SwapPlanner.Kept k = kept.get(i);
            var fam = com.renovator.family.FamilyIndex.get().byId(k.source());
            g.drawString(font, Component.translatable("renovator.swap.kept", k.count(), Component.translatable(k.shape().translationKey()),
                    fam == null ? Component.literal(k.source().toString()) : Families.name(fam), Families.target(k.target(), mixes)), panelX + 8, ly, 0xFFE0B070);
        }
        if (!creative()) g.drawString(font, Component.translatable("renovator.swap.survival_note"), panelX + 8, ly, 0xFF9AA8B4);
        g.drawString(font, note, panelX + 8, ly + 11, 0xFF9AE0A0);
        if (census.isEmpty()) g.drawCenteredString(font, Component.translatable("renovator.swap.empty"), panelX + panelW / 2, panelY + 80, 0xFFB0B0B0);
    }

    // ---------------------------------------------------------------- rows

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    private Button targetButton(Target t, boolean inherited, Runnable onPress) {
        Component label = Families.target(t, mixes);
        if (inherited) label = Component.translatable("renovator.swap.inherited", label);
        return Button.builder(label, b -> onPress.run()).bounds(0, 0, 160, 18).build();
    }

    private final class FamilyRow extends Row {
        final SwapPlanner.Census c;
        final Button toggle, target;

        FamilyRow(SwapPlanner.Census c) {
            this.c = c;
            ResourceLocation id = c.family().id();
            boolean open = expanded.contains(id);
            toggle = Button.builder(Component.literal(open ? "▾" : "▸"), b -> {
                if (!expanded.remove(id)) expanded.add(id);
                rebuildRows();
            }).bounds(0, 0, 14, 18).build();
            toggle.visible = c.shapes().size() > 1;
            target = targetButton(mapping.familyTarget(id), false, () -> openPicker(Families.name(c.family()), t -> {
                mapping = mapping.with(id, t);
                rebuildRows();
            }, id));
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            toggle.setPosition(left, top + 1);
            toggle.render(g, mx, my, pt);
            ItemStack icon = Families.icon(c.family());
            g.renderItem(icon, left + 17, top + 2);
            Component name = Families.name(c.family());
            String count = "×" + c.count();
            int countX = left + width - 162 - 24 - 6 - font.width(count);
            String text = font.plainSubstrByWidth(name.getString(), countX - (left + 37) - 4);
            g.drawString(font, text, left + 37, top + 6, 0xFFFFFFFF);
            g.drawString(font, count, countX, top + 6, 0xFFB0B0B0);
            g.drawString(font, "→", left + width - 162 - 22, top + 6, 0xFF808890);
            target.setPosition(left + width - 162, top + 1);
            target.render(g, mx, my, pt);
            ItemStack ti = Families.targetIcon(mapping.familyTarget(c.family().id()), mixes);
            if (!ti.isEmpty()) g.renderItem(ti, left + width - 162 + 2, top + 1);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(toggle, target);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(toggle, target);
        }
    }

    private final class ShapeRow extends Row {
        final SwapPlanner.Census c;
        final Shape shape;
        final int count;
        final Button target, reset;

        ShapeRow(SwapPlanner.Census c, Shape shape, int count) {
            this.c = c;
            this.shape = shape;
            this.count = count;
            ResourceLocation id = c.family().id();
            Map<Shape, Target> overrides = mapping.shapes().getOrDefault(id, Map.of());
            boolean own = overrides.containsKey(shape);
            target = targetButton(mapping.resolve(id, shape), !own, () -> openPicker(Component.translatable(shape.translationKey()), t -> {
                mapping = mapping.withShape(id, shape, t);
                rebuildRows();
            }, id));
            reset = Button.builder(Component.literal("✕"), b -> {
                mapping = mapping.clearShape(id, shape);
                rebuildRows();
            }).bounds(0, 0, 14, 18).build();
            reset.visible = own;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovered, float pt) {
            g.drawString(font, Component.translatable(shape.translationKey()), left + 37, top + 6, 0xFFC8D0D8);
            String n = "×" + count;
            g.drawString(font, n, left + width - 162 - 24 - 6 - font.width(n), top + 6, 0xFF909090);
            target.setPosition(left + width - 162, top + 1);
            target.render(g, mx, my, pt);
            reset.setPosition(left + 18, top + 1);
            reset.render(g, mx, my, pt);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(target, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(target, reset);
        }
    }
}
