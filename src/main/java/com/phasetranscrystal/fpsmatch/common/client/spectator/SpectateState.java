package com.phasetranscrystal.fpsmatch.common.client.spectator;


import com.phasetranscrystal.fpsmatch.common.spectator.teammate.SpectateMode;

public final class SpectateState {
    private static volatile SpectateMode mode = SpectateMode.FREE;
    public static void set(SpectateMode m){ mode = m == null ? SpectateMode.FREE : m; }
    public static SpectateMode get(){ return mode; }
    public static boolean isAttach(){ return mode == SpectateMode.ATTACH; }
    private SpectateState(){}
}