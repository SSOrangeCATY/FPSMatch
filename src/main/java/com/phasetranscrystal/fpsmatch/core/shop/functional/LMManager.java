package com.phasetranscrystal.fpsmatch.core.shop.functional;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterListenerModuleEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LMManager {
    protected final Map<String, ListenerModule> registry = new HashMap<>();

    public LMManager(){
        MinecraftForge.EVENT_BUS.post(new RegisterListenerModuleEvent(this));
    }

    public void addListenerType(ListenerModule listenerModule){
        String name = listenerModule.getName();
        registry.put(name,listenerModule);
    }

    @Nullable
    public ListenerModule getListenerModule(String name){
        return registry.getOrDefault(name,null);
    }

    public List<String> getListenerModules(){
        return new ArrayList<>(registry.keySet());
    }


    public Map<String, ListenerModule> getRegistry(){
        return registry;
    }

    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event){
        event.registerData(ChangeShopItemModule.class,"ListenerModule", new SaveHolder<>(ChangeShopItemModule.CODEC,ChangeShopItemModule::read, (manager)->{
            FPSMatch.listenerModuleManager.getRegistry().forEach((name,module)->{
                if(module instanceof ChangeShopItemModule cSIM){
                    manager.saveData(cSIM,cSIM.getName());
                }
            });
        }));

    }
}
