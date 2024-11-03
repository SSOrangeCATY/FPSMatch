package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;

public class MapRegister {
    public static void register(){
        FPSMCore.registerGameType("cs",(serverLevel, stringList) -> new CSGameMap(serverLevel,"cs"),true);
      //  FPSMCore.registerGameType("apex",(serverLevel, stringList, spawnPointData) -> new CSGameMap(serverLevel,spawnPointData));
      //  FPSMCore.registerGameType("valorant",(serverLevel, stringList, spawnPointData) -> new CSGameMap(serverLevel,spawnPointData));
    }
}
