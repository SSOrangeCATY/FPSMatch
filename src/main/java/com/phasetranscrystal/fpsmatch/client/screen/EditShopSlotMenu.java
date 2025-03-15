package com.phasetranscrystal.fpsmatch.client.screen;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;


public class EditShopSlotMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final ItemStackHandler itemHandler;
    private final int repoIndex;//itemStackHandler中的物品索引


    public EditShopSlotMenu(int id, Inventory playerInventory, int repoIndex) {
        this(id, playerInventory, new ItemStackHandler(1), new SimpleContainerData(3), repoIndex);
    }

    public EditShopSlotMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, new ItemStackHandler(1), new SimpleContainerData(3), buf.readInt());
    }

    public EditShopSlotMenu(int id, Inventory playerInventory, ItemStackHandler handler, ContainerData data, int repoIndex) {
        super(VanillaGuiRegister.EDIT_SHOP_SLOT_MENU.get(), id);
        this.itemHandler = handler;
        this.data = data;
        this.repoIndex = repoIndex;

        // 左侧物品格子
        this.addSlot(new SlotItemHandler(itemHandler, 0, 20, 20));

        // 玩家物品栏
        addPlayerInventory(playerInventory, 8, 104);

        addDataSlots(data);
    }


    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, x + col * 18, y + 58));
        }
    }

    //交互
    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public int getPrice() {
        return this.data.get(0);
    }

    public int getGroupId() {
        return this.data.get(1);
    }

    public int getListenerId() {
        return this.data.get(2);
    }
}