package com.tacz.guns.client.input;

import com.tacz.guns.GunMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.InputEvent;

public final class TaczKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "tacz"));

    private TaczKeyMappings() {
    }

    public static boolean matches(KeyMapping mapping, InputEvent.Key event) {
        return mapping.matches(event.getKeyEvent());
    }

    public static boolean matchesMouse(KeyMapping mapping, InputEvent.MouseButton event) {
        return mapping.matchesMouse(new MouseButtonEvent(0.0, 0.0, event.getMouseButtonInfo()));
    }
}
