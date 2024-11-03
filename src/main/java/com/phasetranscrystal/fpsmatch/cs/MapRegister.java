package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;

public class MapRegister {
    public static void register(){
        FPSMCore.registerGameType("cs",(serverLevel, stringList) -> new CSGameMap(serverLevel,"cs"),true);
        FPSMCore.registerGameType("apex",(serverLevel, stringList) -> new CSGameMap(serverLevel,"apex"),false);
        FPSMCore.registerGameType("valorant",(serverLevel, stringList) -> new CSGameMap(serverLevel,"valorant"),true);
        FPSMCore.registerGameType("hunt:showdown",(serverLevel, stringList) -> new CSGameMap(serverLevel,"hunt_showdown"),true);
    }
}
