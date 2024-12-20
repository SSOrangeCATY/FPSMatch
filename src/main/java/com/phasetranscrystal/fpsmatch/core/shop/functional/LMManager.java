package com.phasetranscrystal.fpsmatch.core.shop.functional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FileHelper;
import com.phasetranscrystal.fpsmatch.core.event.RegisterListenerModuleEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LMManager {
    protected final Map<String, ListenerModule> registry = new HashMap<>();

    public LMManager(){
        MinecraftForge.EVENT_BUS.post(new RegisterListenerModuleEvent(this));
        List<ChangeShopItemModule> modules = FileHelper.loadListenerModules();
        for (ChangeShopItemModule module : modules) {
            registry.put(module.getName(),module);
        }
    }

    public void addListenerType(ListenerModule listenerModule){
        String name = listenerModule.getName();
        registry.put(name,listenerModule);
    }

    public void save(){
        registry.forEach((name,listenerModule)->{
            FileHelper.saveChangeItemListenerModule(listenerModule);
        });
    }

    @Nullable
    public ListenerModule getListenerModule(String name){
        return registry.getOrDefault(name,null);
    }

    public List<String> getListenerModules(){
        return new ArrayList<>(registry.keySet());
    }

}
