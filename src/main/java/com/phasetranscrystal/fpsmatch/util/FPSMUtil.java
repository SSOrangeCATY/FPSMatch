package com.phasetranscrystal.fpsmatch.util;

import com.google.common.collect.ImmutableList;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public class FPSMUtil {
    public static final List<GunTabType> MAIN_WEAPON = ImmutableList.of(GunTabType.RIFLE,GunTabType.SNIPER,GunTabType.SHOTGUN,GunTabType.SMG,GunTabType.MG);;

    public static final Predicate<ItemStack> MAIN_WEAPON_PREDICATE = (itemStack -> {
        if(itemStack.getItem() instanceof IGun gun){
            return isMainWeapon(gun.getGunId(itemStack));
        }else{
            return false;
        }
    });

    public static final Predicate<ItemStack> SECONDARY_WEAPON_PREDICATE = (itemStack -> {
        if(itemStack.getItem() instanceof IGun gun){
            return getGunTypeByGunId(gun.getGunId(itemStack)).filter(gunTabType -> gunTabType == GunTabType.PISTOL).isPresent();
        }else{
            return false;
        }
    });

    public static final Predicate<ItemStack> THIRD_WEAPON_PREDICATE = (itemStack -> {
        if(itemStack.getItem() instanceof IGun gun){
            return getGunTypeByGunId(gun.getGunId(itemStack)).filter(gunTabType -> gunTabType == GunTabType.RPG).isPresent();
        }else{
            return false;
        }
    });

    public static final Predicate<ItemStack> THROW_PREDICATE = (itemStack -> itemStack.getItem() instanceof IThrowEntityAble);

    public static final Predicate<ItemStack> MISC_PREDICATE = (itemStack -> true);

    public static Optional<GunTabType> getGunTypeByGunId(ResourceLocation gunId){
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(commonGunIndex -> GunTabType.valueOf(commonGunIndex.getType().toUpperCase(Locale.US)));
    }

    public static boolean isMainWeapon(ResourceLocation gunId){
        return getGunTypeByGunId(gunId).filter(MAIN_WEAPON::contains).isPresent();
    }

    public static void setTotalDummyAmmo(ItemStack itemStack, IGun iGun, int amount){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            iGun.useDummyAmmo(itemStack);
            if(amount - maxAmmon > 0) {
                iGun.setCurrentAmmoCount(itemStack,maxAmmon);
                int dummy = amount - maxAmmon;
                iGun.setMaxDummyAmmoAmount(itemStack,dummy);
                iGun.setDummyAmmoAmount(itemStack, dummy);
            }else{
                iGun.setCurrentAmmoCount(itemStack,amount);
                iGun.setDummyAmmoAmount(itemStack,0);
                iGun.setMaxDummyAmmoAmount(itemStack,0);
            }
        }
    }


    public static int getTotalDummyAmmo(ItemStack itemStack, IGun iGun){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            int dummy = iGun.getMaxDummyAmmoAmount(itemStack);
            return maxAmmon + dummy;
        }
        return 0;
    }

    /**
     * use dummy ammo
     * */
    public static void fixGunItem( @NotNull ItemStack itemStack, @NotNull IGun iGun) {
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(gunIndexOptional.isPresent()){
            int maxAmmon = gunIndexOptional.get().getGunData().getAmmoAmount();
            iGun.setCurrentAmmoCount(itemStack,maxAmmon);
        }
        int maxAmmo = iGun.getMaxDummyAmmoAmount(itemStack);
        if(maxAmmo > 0) {
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,maxAmmo);
        }
    }

    /**
     * use dummy ammo
     * */
    public static void resetGunAmmo(ItemStack itemStack, IGun iGun){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            iGun.setCurrentAmmoCount(itemStack,maxAmmon);
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,iGun.getMaxDummyAmmoAmount(itemStack));
        }
    }


    /**
    *  use dummy ammo
    * */
    public static void resetAllGunAmmo(@NotNull ServerPlayer serverPlayer){
        Inventory inventory = serverPlayer.getInventory();
        List<NonNullList<ItemStack>> compartments = ImmutableList.of(inventory.items, inventory.armor, inventory.offhand);
        compartments.forEach((itemList)-> itemList.forEach(itemStack -> {
            if(itemStack.getItem() instanceof IGun iGun){
                resetGunAmmo(itemStack,iGun);
            }
        }));
    }


}
