package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapTypeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class MapRegister {

    public static void onMapRegister(RegisterFPSMapTypeEvent event){
        event.getFpsmCore().registerGameType("cs", CSGameMap::new,true);
    }

}
