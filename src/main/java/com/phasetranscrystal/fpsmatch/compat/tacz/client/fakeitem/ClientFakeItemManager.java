package com.phasetranscrystal.fpsmatch.compat.tacz.client.fakeitem;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ClientFakeItemManager {
    private static ItemStack oldStack = ItemStack.EMPTY;
    private static ItemStack mirrorStack = ItemStack.EMPTY;
    private static boolean isHoldingFake = false;
    private static int fakeSlotIndex = -1;

    public static void equipOrUpdateForSpectator(LocalPlayer player, ItemStack targetStack) {
        if (player == null) return;

        if (!isHoldingFake) {
            Inventory inventory = player.getInventory();
            fakeSlotIndex = inventory.getSelectedSlot();
            oldStack = inventory.getNonEquipmentItems().get(fakeSlotIndex).copy();
            mirrorStack = targetStack == null ? ItemStack.EMPTY : targetStack.copy();
            updatePlayerInventory(player);
            isHoldingFake = true;
            return;
        }

        if (targetStack == null || targetStack.isEmpty()) {
            mirrorStack = ItemStack.EMPTY;
            updatePlayerInventory(player);
            return;
        }

        if (isSameItemType(mirrorStack, targetStack)) {
            syncMirrorStackData(mirrorStack, targetStack);
        } else {
            mirrorStack = targetStack.copy();
            updatePlayerInventory(player);
        }
    }

    public static void revertFakeItem(LocalPlayer player) {
        if (!isHoldingFake) return;
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        if (fakeSlotIndex >= 0 && fakeSlotIndex < items.size()) {
            items.set(fakeSlotIndex, oldStack);
        }
        resetState();
    }

    public static void tickUpdate(LocalPlayer player) {
        if (!isHoldingFake || player == null) {
            return;
        }
        Inventory inv = player.getInventory();
        NonNullList<ItemStack> items = inv.getNonEquipmentItems();
        int selected = inv.getSelectedSlot();
        if (selected == fakeSlotIndex) {
            return;
        }
        if (selected < 0 || selected >= items.size()) {
            return;
        }
        if (fakeSlotIndex >= 0 && fakeSlotIndex < items.size()) {
            items.set(fakeSlotIndex, oldStack);
        }
        fakeSlotIndex = selected;
        oldStack = items.get(fakeSlotIndex).copy();
        items.set(fakeSlotIndex, mirrorStack.copy());

        if (!isHoldingFake || fakeSlotIndex < 0 || fakeSlotIndex >= items.size()) return;
        if (!items.get(fakeSlotIndex).equals(mirrorStack)) {
            updatePlayerInventory(player);
        }
    }

    private static void updatePlayerInventory(LocalPlayer player) {
        player.getInventory().getNonEquipmentItems().set(fakeSlotIndex, mirrorStack.copy());
    }

    private static void resetState() {
        oldStack = ItemStack.EMPTY;
        mirrorStack = ItemStack.EMPTY;
        fakeSlotIndex = -1;
        isHoldingFake = false;
    }

    private static boolean isSameItemType(ItemStack a, ItemStack b) {
        Item ia = a.isEmpty() ? null : a.getItem();
        Item ib = b.isEmpty() ? null : b.getItem();
        return ia != null && ia == ib;
    }

    private static void syncMirrorStackData(ItemStack mirror, ItemStack target) {
        if (mirror.isEmpty() || target.isEmpty()) return;
        mirror.applyComponentsAndValidate(target.getComponentsPatch());
        mirror.setDamageValue(target.getDamageValue());
        mirror.setCount(target.getCount());
    }

    public static boolean isHoldingFakeItem() {
        return isHoldingFake;
    }

    public static int getFakeSlotIndex() {
        return fakeSlotIndex;
    }

    public static ItemStack getCurrentFakeStack() {
        return mirrorStack;
    }

    public static boolean isFakeGun() {
        return isHoldingFake && mirrorStack.getItem() instanceof com.tacz.guns.api.item.IGun;
    }
}
