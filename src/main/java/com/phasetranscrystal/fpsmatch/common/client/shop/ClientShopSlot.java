package com.phasetranscrystal.fpsmatch.common.client.shop;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;


public class ClientShopSlot{
    private ItemStack itemStack;
    private int cost;
    @Nullable
    private Identifier texture = null;
    private int boughtCount = 0;
    private boolean locked = false;
    private int groupId = -1;

    public static ClientShopSlot empty(){
        return new ClientShopSlot(ItemStack.EMPTY,0);
    }

    private ClientShopSlot(ItemStack itemStack, int defaultCost) {
        this.itemStack = itemStack;
        this.cost = defaultCost;
    }

    public int groupId(){
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public int cost(){
        return cost;
    }

    public Identifier texture(){
        return texture;
    }

    public ItemStack itemStack(){
        return itemStack;
    }

    public int boughtCount(){
        return boughtCount;
    }

    public boolean isLocked(){
        return locked;
    }

    public void setLock(boolean lock){
        this.locked = lock;
    }

    public void setBoughtCount(int count){
        this.boughtCount = count;
    }
    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setTexture(@Nullable Identifier hudTexture) {
        this.texture = hudTexture;
    }

    public String name() {
        return this.itemStack.getDisplayName().getString().replace("[","").replace("]","").replaceAll("§.", "");
    }

    public boolean canReturn() {
        return this.boughtCount > 0 && !locked;
    }
}
