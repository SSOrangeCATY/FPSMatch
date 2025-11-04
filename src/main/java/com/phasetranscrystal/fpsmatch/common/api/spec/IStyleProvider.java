
package com.phasetranscrystal.fpsmatch.common.api.spec;

import java.util.UUID;

public interface IStyleProvider {
    int stripeColor(UUID uuid, String team, int vanilla);

    float nameScale(float vanilla);

    IStyleProvider DEFAULT = new IStyleProvider() {
        @Override public int stripeColor(UUID u,String t,int v){ return v; }
        @Override public float nameScale(float v){ return v; }
    };
}