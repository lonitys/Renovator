package com.renovator.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Asks for a name (blueprints, templates, mixes). */
public final class NameInputScreen extends RenovatorScreen {
    private String value;
    private final Consumer<String> onDone;
    private EditBox box;

    public NameInputScreen(@Nullable Screen parent, Component title, String initial, Consumer<String> onDone) {
        super(title, parent);
        this.value = initial;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int w = 260, h = 82, x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        box = new EditBox(font, x + 10, y + 24, w - 20, 18, title);
        box.setMaxLength(64);
        box.setValue(value);
        box.setResponder(v -> value = v);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> done()).bounds(x + 10, y + 52, 115, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w - 125, y + 52, 115, 20).build());
    }

    private void done() {
        String v = box.getValue().trim();
        if (v.isEmpty()) return;
        minecraft.setScreen(parent);
        onDone.accept(v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitle(g);
    }
}
