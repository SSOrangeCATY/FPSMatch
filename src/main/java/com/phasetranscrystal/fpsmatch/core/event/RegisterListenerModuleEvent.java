package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class RegisterListenerModuleEvent extends Event {
    LMManager manager;

    public RegisterListenerModuleEvent(LMManager lMManager){
        this.manager = lMManager;
    }


    /**
     * 注册硬编码的监听模块
     * */
    public void register(ListenerModule listenerModule){
        this.manager.addListenerType(listenerModule);
    }

}
