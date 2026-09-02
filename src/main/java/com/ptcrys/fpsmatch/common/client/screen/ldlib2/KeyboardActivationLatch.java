package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/** Suppresses keyboard auto-repeat until the matching activation key is released. */
public final class KeyboardActivationLatch {

    private final Set<Integer> heldKeys = new HashSet<>();

    public boolean press(int keyCode) {
        return isActivationKey(keyCode) && heldKeys.add(keyCode);
    }

    public boolean release(int keyCode) {
        return isActivationKey(keyCode) && heldKeys.remove(keyCode);
    }

    public boolean isHeld(int keyCode) {
        return heldKeys.contains(keyCode);
    }

    public void reset() {
        heldKeys.clear();
    }

    public static boolean isActivationKey(int keyCode) {
        return keyCode == GLFW_KEY_ENTER || keyCode == GLFW_KEY_KP_ENTER || keyCode == GLFW_KEY_SPACE;
    }
}
