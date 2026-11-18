package com.stilwerk.client.screen;

import com.stilwerk.blueprint.Blueprint;
import com.stilwerk.blueprint.BlueprintMeta;
import com.stilwerk.client.ClientLibrary;
import com.stilwerk.client.ClientState;
import com.stilwerk.client.PlacementController;
import com.stilwerk.client.WandClient;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import java.io.IOException;
import java.time.LocalDate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Blueprint mode menu: copy the selection, open the library, restyle or stop the current placement. */
public final class BlueprintMenuScreen extends StilwerkScreen {
    public BlueprintMenuScreen() {
        super(Component.translatable("stilwerk.blueprint.title"), null);
    }

    @Override
    protected void init() {
        boolean placing = PlacementController.active();
        boolean creative = minecraft.player != null && minecraft.player.isCreative();
        int rows = 2 + (placing ? 2 + (creative ? 1 : 0) : 0);
        int w = 220, h = 30 + rows * 24 + 6, x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        int by = y + 26;
        Button copy = Button.builder(Component.translatable("stilwerk.blueprint.copy"), b -> copy()).bounds(x + 10, by, w - 20, 20).build();
        copy.active = ClientState.selection() != null;
        addRenderableWidget(copy);
        by += 24;
        addRenderableWidget(Button.builder(Component.translatable("stilwerk.blueprint.library"), b -> minecraft.setScreen(new LibraryScreen(null)))
                .bounds(x + 10, by, w - 20, 20).build());
        if (placing) {
            by += 24;
            addRenderableWidget(Button.builder(Component.translatable("stilwerk.blueprint.restyle"), b -> WandClient.openSwap())
                    .bounds(x + 10, by, w - 20, 20).build());
            if (creative) {
                by += 24;
                addRenderableWidget(Button.builder(overwriteLabel(), b -> {
                    PlacementController.overwrite = !PlacementController.overwrite;
                    b.setMessage(overwriteLabel());
                }).bounds(x + 10, by, w - 20, 20).build());
            }
            by += 24;
            addRenderableWidget(Button.builder(Component.translatable("stilwerk.blueprint.stop"), b -> {
                PlacementController.stop();
                onClose();
            }).bounds(x + 10, by, w - 20, 20).build());
        }
    }

    private Component overwriteLabel() {
        return Component.translatable(PlacementController.overwrite ? "stilwerk.blueprint.overwrite_on" : "stilwerk.blueprint.overwrite_off");
    }

    private void copy() {
        Selection sel = ClientState.selection();
        if (sel == null || minecraft.level == null) return;
        ResolvedSelection res = sel.resolve(minecraft.level, ClientState.CLIENT_LIMIT);
        if (res.truncated() || res.isEmpty()) {
            ClientState.actionbar(Component.translatable("stilwerk.select.too_big"));
            return;
        }
        String initial = Component.translatable("stilwerk.blueprint.default_name", LocalDate.now().toString()).getString();
        minecraft.setScreen(new NameInputScreen(null, Component.translatable("stilwerk.blueprint.name"), initial, name -> {
            String author = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
            Blueprint bp = Blueprint.capture(minecraft.level, res, true, minecraft.level.registryAccess(),
                    new BlueprintMeta(name, author, System.currentTimeMillis(), "stilwerk"));
            try {
                ClientLibrary.save(bp);
                PlacementController.start(bp);
                ClientState.actionbar(Component.translatable("stilwerk.blueprint.saved", name, bp.nonAir()));
            } catch (IOException e) {
                ClientState.actionbar(Component.translatable("stilwerk.error.save", e.getMessage()));
            }
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
    }
}
