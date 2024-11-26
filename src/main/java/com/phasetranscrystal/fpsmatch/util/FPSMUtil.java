package com.phasetranscrystal.fpsmatch.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class FPSMUtil {
    public static void fixGunItem( @NotNull ItemStack itemStack, @NotNull IGun iGun) {
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(gunIndexOptional.isPresent()){
            GunData gunData = gunIndexOptional.get().getGunData();
            iGun.setCurrentAmmoCount(itemStack,gunData.getAmmoAmount());
        }
        int maxAmmo = iGun.getMaxDummyAmmoAmount(itemStack);
        if(maxAmmo > 0) {
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,maxAmmo);
        }
    }
}
