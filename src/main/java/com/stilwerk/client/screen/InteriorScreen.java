package com.stilwerk.client.screen;

import com.stilwerk.client.InteriorController;
import com.stilwerk.interior.ModuleLibrary;
import com.stilwerk.interior.Room;
import com.stilwerk.interior.RoomType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Room info and the room types to furnish it with. */
public final class InteriorScreen extends StilwerkScreen {
    private static final ItemStack[] ICONS = {new ItemStack(Items.RED_BED), new ItemStack(Items.PAINTING), new ItemStack(Items.SMOKER),
            new ItemStack(Items.CAKE), new ItemStack(Items.BOOKSHELF), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.ANVIL),
            new ItemStack(Items.CHEST)};
    private final Room room;

    public InteriorScreen(Room room) {
        super(Component.translatable("stilwerk.interior.title"), null);
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
            Component label = Component.translatable(id.equals("auto") ? "stilwerk.interior.type.auto" : "stilwerk.interior.type." + id);
            addRenderableWidget(Button.builder(label, b -> {
                minecraft.setScreen(null);
                InteriorController.start(id);
            }).bounds(bx, by, bw, bh).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 8, y + h - 26, w - 16, 20).build());
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
        g.drawString(font, Component.translatable("stilwerk.interior.info", room.width(), room.depth(), maxH, room.floor().size(),
                room.doors().size(), windowCount()), panelX + 8, panelY + 22, 0xFFB8C4CE);
        List<String> mods = ModuleLibrary.furnitureMods();
        Component modLine = mods.isEmpty() ? Component.translatable("stilwerk.interior.vanilla")
                : Component.translatable("stilwerk.interior.mods", String.join(", ", mods));
        g.drawString(font, modLine, panelX + 8, panelY + 36, mods.isEmpty() ? 0xFF9AA8B4 : 0xFF9AE0A0);
        g.drawString(font, Component.translatable("stilwerk.interior.pick"), panelX + 8, panelY + 52, 0xFFE8D9A8);
        for (int i = 0; i < ICONS.length; i++) {
            int idx = i + 1;
            int bx = panelX + 8 + (idx % 3) * 108, by = panelY + 66 + (idx / 3) * 26;
            g.renderItem(ICONS[i], bx + 3, by + 3);
        }
    }
}
