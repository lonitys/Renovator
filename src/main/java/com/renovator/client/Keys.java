package com.renovator.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings; all only act while the wand is in the main hand. Defaults are free in the user's pack. */
public final class Keys {
    private Keys() {}

    public static final String CATEGORY = "key.categories.renovator";

    public static final KeyMapping MODE_WHEEL = key("mode_wheel", GLFW.GLFW_KEY_M);
    public static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping TOGGLE_PREVIEW = key("toggle_preview", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping ROTATE_LEFT = key("rotate_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping ROTATE_RIGHT = key("rotate_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_UP);
    public static final KeyMapping UNDO = key("undo", GLFW.GLFW_KEY_U);
    public static final KeyMapping LAYER_UP = key("layer_up", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping LAYER_DOWN = key("layer_down", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping PROJECTION_VIEW = key("projection_view", GLFW.GLFW_KEY_END);
    public static final KeyMapping PROJECTION_STYLE = key("projection_style", GLFW.GLFW_KEY_HOME);

    public static final KeyMapping[] ALL = {MODE_WHEEL, CONFIRM, TOGGLE_PREVIEW, ROTATE_LEFT, ROTATE_RIGHT, MIRROR, UNDO,
            LAYER_UP, LAYER_DOWN, PROJECTION_VIEW, PROJECTION_STYLE};

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.renovator." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM.getOrCreate(code), CATEGORY);
    }
}
