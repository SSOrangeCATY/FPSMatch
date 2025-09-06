package com.phasetranscrystal.fpsmatch.common.shop.functional;


import com.phasetranscrystal.fpsmatch.common.attributes.ammo.BulletproofArmorAttribute;
import com.phasetranscrystal.fpsmatch.core.shop.event.ShopSlotChangeEvent;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;

public class BulletproofArmorWithHelmetListenerModule implements ListenerModule {
    @Override
    public void onChange(ShopSlotChangeEvent event) {
        if (event.flag >= 1) {
            BulletproofArmorAttribute.addPlayer(event.player,new BulletproofArmorAttribute(true));
        }else{
            BulletproofArmorAttribute.removePlayer(event.player);
        }
    }

    @Override
    public String getName() {
        return "bulletproof_with_helmet";
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
