package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    public static final Map<String,BaseMap> games = new HashMap<>();
    @Nullable public static BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
         games.values().forEach((baseMap -> {
            if(baseMap.checkGameHasPlayer(player)){
                map.set(baseMap);
            }
         }));
         return map.get();
    }

    public static void registerMap(String mapName ,BaseMap map){
        games.put(mapName,map);
    }

    @Nullable public static BaseMap getMapByName(String name){
       return games.getOrDefault(name,null);
    }

    public static List<String> getMapNames(){
        return games.keySet().stream().toList();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            games.values().forEach((BaseMap::mapTick));
        }
    }

}
