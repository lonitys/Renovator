package com.stilwerk.client.screen;

import com.stilwerk.client.Families;
import com.stilwerk.family.Family;
import com.stilwerk.family.FamilyIndex;
import com.stilwerk.mix.MixPalette;
import com.stilwerk.swap.Target;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Picks a target: keep, a mix palette, a new mix, or any family (searchable). */
public final class FamilyPickerScreen extends StilwerkScreen {
    public record Options(boolean allowKeep, boolean allowMixes) {}

    private final Options options;
    private final Map<String, MixPalette> mixes;
    private final Consumer<Target> onPick;
    private final @Nullable Consumer<Consumer<MixPalette>> newMix;
    private String query = "";
    private EditBox search;
    private Choices list;

    /**
     * @param newMix opens the mix editor; it calls back with the saved mix (null hides "New mix")
     */
    public FamilyPickerScreen(Screen parent, Component title, Options options, Map<String, MixPalette> mixes,
                              Consumer<Target> onPick, @Nullable Consumer<Consumer<MixPalette>> newMix) {
        super(title, parent);
        this.options = options;
        this.mixes = mixes;
        this.onPick = onPick;
        this.newMix = newMix;
    }

    @Override
    protected void init() {
        int w = Math.min(340, width - 20), h = height - 30, x = (width - w) / 2, y = 15;
        panel(x, y, w, h);
        search = new EditBox(font, x + 8, y + 20, w - 16, 16, Component.translatable("stilwerk.picker.search"));
        search.setValue(query);
        search.setResponder(v -> {
            query = v;
            refill();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        list = new Choices(x + 4, y + 42, w - 8, h - 72);
        addRenderableWidget(list);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w - 108, y + h - 26, 100, 20).build());
        refill();
    }

    private void pick(Target t) {
        minecraft.setScreen(parent);
        onPick.accept(t);
    }

    private void refill() {
        List<Choice> rows = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            if (options.allowKeep()) rows.add(new Choice(Component.translatable("stilwerk.target.keep"), ItemStack.EMPTY, () -> pick(Target.keep())));
            if (options.allowMixes()) {
                for (MixPalette p : mixes.values()) {
                    Family base = FamilyIndex.get().byId(p.base());
                    rows.add(new Choice(Component.translatable("stilwerk.target.mix", Families.name(p)),
                            base == null ? ItemStack.EMPTY : Families.icon(base), () -> pick(Target.mix(p.id()))));
                }
                if (newMix != null) {
                    rows.add(new Choice(Component.translatable("stilwerk.picker.new_mix"), new ItemStack(net.minecraft.world.item.Items.BRUSH),
                            () -> newMix.accept(saved -> pick(Target.mix(saved.id())))));
                }
            }
        }
        List<Family> fams = new ArrayList<>(FamilyIndex.get().all());
        fams.sort(Comparator.comparingInt((Family f) -> f.wood() ? 0 : f.singleton() ? 2 : 1).thenComparing(f -> f.id().toString()));
        for (Family f : fams) {
            Component name = Families.name(f);
            if (!q.isEmpty() && !name.getString().toLowerCase(Locale.ROOT).contains(q) && !f.id().toString().contains(q)) continue;
            rows.add(new Choice(name, Families.icon(f), () -> pick(Target.family(f.id()))));
        }
        list.setAll(rows);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
    }

    private final class Choices extends ObjectSelectionList<Choice> {
        Choices(int x, int y, int w, int h) {
            super(FamilyPickerScreen.this.minecraft, w, h, y, 20);
            setX(x);
        }

        void setAll(List<Choice> rows) {
            replaceEntries(rows);
            setScrollAmount(0);
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

    private final class Choice extends ObjectSelectionList.Entry<Choice> {
        private final Component label;
        private final ItemStack icon;
        private final Runnable action;

        Choice(Component label, ItemStack icon, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float pt) {
            if (hovered) g.fill(left, top, left + width, top + height, 0x40FFFFFF);
            if (!icon.isEmpty()) g.renderItem(icon, left + 2, top + 1);
            g.drawString(font, label, left + 24, top + 6, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            action.run();
            return true;
        }

        @Override
        public Component getNarration() {
            return label;
        }
    }
}
