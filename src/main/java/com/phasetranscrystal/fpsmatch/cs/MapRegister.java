package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;

public class MapRegister {
    public static void register(){
        FPSMCore.registerGameType("cs",(serverLevel, stringList, mapName) -> new CSGameMap(serverLevel,mapName),true);
        FPSMCore.registerGameType("apex",(serverLevel, stringList, mapName) -> new CSGameMap(serverLevel,mapName),false);
        FPSMCore.registerGameType("valorant",(serverLevel, stringList, mapName) -> new CSGameMap(serverLevel,mapName),true);
        FPSMCore.registerGameType("hunt:showdown",(serverLevel, stringList, mapName) -> new CSGameMap(serverLevel,mapName),true);
    }
}
