package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class MapRegister {

    @SubscribeEvent
    public static void onMapRegister(RegisterFPSMapEvent event){
        event.getFpsmCore().registerGameType("cs", CSGameMap::new,true);
    }

}
