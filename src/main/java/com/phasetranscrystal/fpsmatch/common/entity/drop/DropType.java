package com.phasetranscrystal.fpsmatch.common.entity.drop;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

public enum DropType {
    MAIN_WEAPON((stack -> {
        for(Predicate<ItemStack> predicate : FPSMUtil.MAIN_WEAPON_PREDICATE){
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    }),0),
    SECONDARY_WEAPON((stack -> {
        for (Predicate<ItemStack> predicate : FPSMUtil.SECONDARY_WEAPON_PREDICATE) {
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    }),1),
    THIRD_WEAPON((stack -> {
        for (Predicate<ItemStack> predicate : FPSMUtil.THIRD_WEAPON_PREDICATE) {
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    }),2),
    THROW((stack -> {
        for (Predicate<ItemStack> predicate : FPSMUtil.THROW_PREDICATE) {
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    }),4,5,6,7),
    MISC((stack -> true),8);

    private final Predicate<ItemStack> predicate;
    private final int[] slotIndex;

    DropType(Predicate<ItemStack> predicate, int... slotIndex) {
        this.predicate = predicate;
        this.slotIndex = slotIndex;
    }

    public Predicate<Player> inventoryMatch(){
        return (player) -> {
            int i = player.getInventory().clearOrCountMatchingItems(predicate, 0, player.inventoryMenu.getCraftSlots());
            return i < getLimit(this);
        };
    }

    public Predicate<ItemStack> itemMatch(){
        return predicate;
    }

    public static int getLimit(DropType type){
        return switch (type) {
            case MAIN_WEAPON -> FPSMConfig.common.mainWeaponCount.get();
            case SECONDARY_WEAPON -> FPSMConfig.common.secondaryWeaponCount.get();
            case THIRD_WEAPON -> FPSMConfig.common.thirdWeaponCount.get();
            case THROW -> FPSMConfig.common.throwableCount.get();
            case MISC -> Integer.MAX_VALUE;
        };
    }

    public static DropType getItemDropType(ItemStack itemStack) {
        for (DropType type : DropType.values()) {
            if(type.itemMatch().test(itemStack)){
                return type;
            }
        }
        return DropType.MISC;
    }

    public int[] getSlotIndex() {
        return slotIndex;
    }
}