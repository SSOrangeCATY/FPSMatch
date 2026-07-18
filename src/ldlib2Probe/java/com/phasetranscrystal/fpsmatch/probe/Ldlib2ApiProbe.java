package com.phasetranscrystal.fpsmatch.probe;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** Compile-only compatibility probe; this source set is never packaged. */
public final class Ldlib2ApiProbe {
    private Ldlib2ApiProbe() {
    }

    public static UI build(String rootId) {
        UIElement root = new UIElement().setId(rootId);
        UI ui = UI.of(root);
        if (ui.selectId(rootId).findFirst().orElseThrow() != root) {
            throw new IllegalStateException("LDLib2 ID selection is inconsistent");
        }
        return ui;
    }
}
