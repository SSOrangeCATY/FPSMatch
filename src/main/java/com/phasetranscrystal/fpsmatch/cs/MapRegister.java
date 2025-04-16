package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MapRegister {

    @SubscribeEvent
    public static void onMapRegister(RegisterFPSMapEvent event){
        event.registerGameType("cs", CSGameMap::new,true);
    }
    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event){
        event.registerData(CSGameMap.class,"CSGameMaps",
                new SaveHolder.Builder<>(CSGameMap.CODEC)
                        .withReadHandler(CSGameMap::read)
                        .withWriteHandler(CSGameMap::write)
                        .build()
        );
    }
}
