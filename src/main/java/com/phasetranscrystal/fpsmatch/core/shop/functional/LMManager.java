package com.phasetranscrystal.fpsmatch.core.shop.functional;

import com.phasetranscrystal.fpsmatch.core.event.RegisterListenerModuleEvent;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class LMManager {
    protected final Map<String,ListenerModule> registry = new HashMap<>();

    public LMManager(){
        MinecraftForge.EVENT_BUS.post(new RegisterListenerModuleEvent(this));
    }

    public void addListenerType(ListenerModule listenerModule){
        registry.put(listenerModule.getName(),listenerModule);
    }

    @Nullable
    public ListenerModule getListenerModule(String name){
        return registry.getOrDefault(name,null);
    }
}
