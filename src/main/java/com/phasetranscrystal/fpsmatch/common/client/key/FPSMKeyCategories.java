package com.phasetranscrystal.fpsmatch.common.client.key;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class FPSMKeyCategories {
    public static final KeyMapping.Category FPSM = new KeyMapping.Category(Identifier.fromNamespaceAndPath(FPSMatch.MODID, "fpsm"));

    private FPSMKeyCategories() {
    }
}
