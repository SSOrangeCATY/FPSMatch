package com.phasetranscrystal.fpsmatch.core.event.register;

import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import net.minecraftforge.eventbus.api.Event;

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
