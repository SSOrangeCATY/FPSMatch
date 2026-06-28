package com.tacz.guns.api.event.common;

import com.tacz.guns.compat.kubejs.events.TimelessClientEvents;
import com.tacz.guns.compat.kubejs.events.TimelessCommonEvents;
import com.tacz.guns.compat.kubejs.events.TimelessServerEvents;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModList;

public interface KubeJSGunEventPoster<E extends Event> {
    default void postEventToKubeJS(E event) {
        if (isModLoaded("kubejs")) {
            TimelessCommonEvents.INSTANCE.postKubeJSEvent(event);
        }
    }

    //客户端事件应调用此方法
    default void postClientEventToKubeJS(E event) {
        if (isModLoaded("kubejs")) {
            TimelessClientEvents.INSTANCE.postKubeJSEvent(event);
        }
    }

    //服务端事件应调用此方法
    default void postServerEventToKubeJS(E event) {
        if (isModLoaded("kubejs")) {
            TimelessServerEvents.INSTANCE.postKubeJSEvent(event);
        }
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }
}
