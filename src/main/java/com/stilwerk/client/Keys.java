package com.stilwerk.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings; all only act while the wand is in the main hand. Defaults are free in the user's pack. */
public final class Keys {
    private Keys() {}

    public static final String CATEGORY = "key.categories.stilwerk";

    public static final KeyMapping MODE_WHEEL = key("mode_wheel", GLFW.GLFW_KEY_M);
    public static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping TOGGLE_PREVIEW = key("toggle_preview", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping ROTATE_LEFT = key("rotate_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping ROTATE_RIGHT = key("rotate_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_UP);
    public static final KeyMapping UNDO = key("undo", GLFW.GLFW_KEY_U);

    public static final KeyMapping[] ALL = {MODE_WHEEL, CONFIRM, TOGGLE_PREVIEW, ROTATE_LEFT, ROTATE_RIGHT, MIRROR, UNDO};

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.stilwerk." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM.getOrCreate(code), CATEGORY);
    }
}
