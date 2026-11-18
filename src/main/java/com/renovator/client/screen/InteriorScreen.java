package com.renovator.client.screen;

import com.renovator.client.InteriorController;
import com.renovator.interior.FurnitureChoice;
import com.renovator.interior.ModuleLibrary;
import com.renovator.interior.Room;
import com.renovator.interior.RoomType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Room info and the room types to furnish it with. */
public final class InteriorScreen extends RenovatorScreen {
    private static final ItemStack[] ICONS = {new ItemStack(Items.RED_BED), new ItemStack(Items.PAINTING), new ItemStack(Items.SMOKER),
            new ItemStack(Items.CAKE), new ItemStack(Items.BOOKSHELF), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.ANVIL),
            new ItemStack(Items.CHEST)};
    private final Room room;

    public InteriorScreen(Room room) {
        super(Component.translatable("renovator.interior.title"), null);
        this.room = room;
    }

    @Override
    protected void init() {
        List<RoomType> types = ModuleLibrary.types();
        int cols = 3, bw = 104, bh = 22;
        int rows = (types.size() + 1 + cols - 1) / cols;
        int w = cols * (bw + 4) + 12, h = 70 + rows * (bh + 4) + 30, x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        List<String> ids = new ArrayList<>();
        ids.add("auto");
        for (RoomType t : types) ids.add(t.id());
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int bx = x + 8 + (i % cols) * (bw + 4), by = y + 66 + (i / cols) * (bh + 4);
            Component label = Component.translatable(id.equals("auto") ? "renovator.interior.type.auto" : "renovator.interior.type." + id);
            addRenderableWidget(Button.builder(label, b -> {
                minecraft.setScreen(null);
                InteriorController.start(id);
            }).bounds(bx, by, bw, bh).build());
        }
        Button furniture = Button.builder(furnitureLabel(), b -> {
            InteriorController.cycleFurniture();
            b.setMessage(furnitureLabel());
        }).bounds(x + 8, y + 32, w - 16, 18).build();
        furniture.active = FurnitureChoice.options().size() > 1;
        furniture.setTooltip(Tooltip.create(Component.translatable("renovator.interior.furniture.tip")));
        addRenderableWidget(furniture);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 8, y + h - 26, w - 16, 20).build());
    }

    private static Component furnitureLabel() {
        return Component.translatable("renovator.interior.furniture", FurnitureChoice.label(InteriorController.furniture()));
    }

    /** Windows = connected groups of glass blocks. */
    private int windowCount() {
        java.util.Set<net.minecraft.core.BlockPos> left = new java.util.HashSet<>(room.windows());
        int n = 0;
        while (!left.isEmpty()) {
            n++;
            java.util.ArrayDeque<net.minecraft.core.BlockPos> q = new java.util.ArrayDeque<>();
            net.minecraft.core.BlockPos first = left.iterator().next();
            left.remove(first);
            q.add(first);
            while (!q.isEmpty()) {
                net.minecraft.core.BlockPos c = q.poll();
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    if (left.remove(c.relative(d))) q.add(c.relative(d));
                }
            }
        }
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
        int maxH = 0;
        for (long k : room.floor()) maxH = Math.max(maxH, room.height(k));
        g.drawString(font, Component.translatable("renovator.interior.info", room.width(), room.depth(), maxH, room.floor().size(),
                room.doors().size(), windowCount()), panelX + 8, panelY + 22, 0xFFB8C4CE);
        g.drawString(font, Component.translatable("renovator.interior.pick"), panelX + 8, panelY + 52, 0xFFE8D9A8);
        for (int i = 0; i < ICONS.length; i++) {
            int idx = i + 1;
            int bx = panelX + 8 + (idx % 3) * 108, by = panelY + 66 + (idx / 3) * 26;
            g.renderItem(ICONS[i], bx + 3, by + 3);
        }
    }
}
