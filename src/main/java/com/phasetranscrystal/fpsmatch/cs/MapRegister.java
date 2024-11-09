package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;

public class MapRegister {
    public static void register(){
        FPSMCore.registerGameType("cs", CSGameMap::new,true);
    }
    
}
