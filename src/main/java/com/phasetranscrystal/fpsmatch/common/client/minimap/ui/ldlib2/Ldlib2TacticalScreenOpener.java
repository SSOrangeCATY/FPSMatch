package com.phasetranscrystal.fpsmatch.common.client.minimap.ui.ldlib2;

import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.MinimapClientScreens;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.function.Supplier;

public final class Ldlib2TacticalScreenOpener
        implements MinimapClientScreens.ScreenOpener {
    private final Supplier<Ldlib2MinimapHudPresentation> presentations;

    public Ldlib2TacticalScreenOpener(
            Supplier<Ldlib2MinimapHudPresentation> presentations
    ) {
        this.presentations = Objects.requireNonNull(
                presentations, "presentations"
        );
    }

    @Override
    public void open(TacticalMapController controller, Runnable onClose) {
        Ldlib2MinimapHudPresentation presentation = presentations.get();
        if (presentation == null) {
            onClose.run();
            return;
        }
        Minecraft.getInstance().gui.setScreen(new Ldlib2TacticalMapScreen(
                controller, onClose, presentation
        ));
    }

    @Override
    public void close() {
        if (Minecraft.getInstance().gui.screen()
                instanceof Ldlib2TacticalMapScreen) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
