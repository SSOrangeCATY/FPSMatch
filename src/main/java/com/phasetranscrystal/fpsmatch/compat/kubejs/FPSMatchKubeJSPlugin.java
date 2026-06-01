package com.phasetranscrystal.fpsmatch.compat.kubejs;

import com.phasetranscrystal.fpsmatch.compat.kubejs.events.FPSMatchCommonEvents;
import com.phasetranscrystal.fpsmatch.compat.kubejs.events.FPSMatchKubeJSEvents;
import dev.latvian.mods.kubejs.KubeJSPlugin;

public class FPSMatchKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        FPSMatchCommonEvents.INSTANCE.init();
        FPSMatchKubeJSEvents.GROUP.register();
    }
}
