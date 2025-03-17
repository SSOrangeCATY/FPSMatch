package com.phasetranscrystal.fpsmatch.client.screen;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.codec.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.item.ShopEditTool;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;


public class EditShopSlotMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private ItemStackHandler itemHandler;
    private ShopSlot shopSlot;
    private ItemStack guiItemStack;
    private int repoIndex;

    public EditShopSlotMenu(int id, Inventory playerInventory, ShopSlot shopSlot, ItemStack guiItemStack, int repoIndex) {
        this(id, playerInventory, new ItemStackHandler(1), new SimpleContainerData(3), shopSlot, guiItemStack, repoIndex);
    }

    public EditShopSlotMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, FPSMCodec.decodeShopSlotFromJson(new Gson().fromJson(buf.readUtf(), JsonElement.class)), buf.readItem(), buf.readInt());
    }

    public EditShopSlotMenu(int id, Inventory playerInventory, ItemStackHandler handler, ContainerData data, ShopSlot shopSlot, ItemStack guiItemStack, int repoIndex) {
        super(VanillaGuiRegister.EDIT_SHOP_SLOT_MENU.get(), id);
        this.itemHandler = handler;
        this.data = data;
        this.shopSlot = shopSlot;
        this.guiItemStack = guiItemStack;
        this.repoIndex = repoIndex;
        this.setAmmo(shopSlot.getAmmoCount());
        this.setPrice(shopSlot.getDefaultCost());
        this.setGroupId(shopSlot.getGroupId());
        this.itemHandler.setStackInSlot(0, this.shopSlot.process());
        // 左侧物品格子
        this.addSlot(new SlotItemHandler(itemHandler, 0, 20, 20));

        // 玩家物品栏
        addPlayerInventory(playerInventory, 8, 124);

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

    //shift 交互忽略
    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        //WIP 类型检验
        return true;
    }

    @Override
    public void removed(Player pPlayer) {
        super.removed(pPlayer);
        if (pPlayer instanceof ServerPlayer serverPlayer) {
            System.out.println("保存内容!:" + System.identityHashCode(this.shopSlot));

            if (guiItemStack.getItem() instanceof ShopEditTool shopEditTool) {
                BaseMap map = FPSMCore.getInstance().getMapByName(shopEditTool.getTag(guiItemStack, ShopEditTool.MAP_TAG));
                if (map instanceof ShopMap<?> shopMap) {
                    FPSMShop shop = shopMap.getShop(shopEditTool.getTag(guiItemStack, ShopEditTool.SHOP_TAG));

                    //保存内容,先保存物品后设置内容
                    shopSlot.setItemSupplier(() -> itemHandler.getStackInSlot(0));
                    ItemStack slotStack = shopSlot.process();
                    shopSlot.setDefaultCost(this.getPrice());
                    shopSlot.setGroupId(this.getGroupId());
                    if (slotStack.getItem() instanceof IGun iGun) {
                        FPSMUtil.setTotalDummyAmmo(slotStack, iGun, this.getAmmo());
                    }
                    System.out.println("保存内容!:" + this.getPrice());
                    shop.replaceDefaultShopData(ItemType.values()[this.repoIndex % 5], this.repoIndex / 5, shopSlot);
                    //同步
                    shop.syncShopData();
                }

            }

            //WIP:不知道哪里寄了，求救，好像会有循环调用
//            // 返回上级菜单
//            NetworkHooks.openScreen(serverPlayer,
//                    new SimpleMenuProvider(
//                            (windowId, inv, p) -> {
//                                return new EditorShopContainer(windowId, inv); // 创建容器并传递物品
//                            },
//                            Component.translatable("gui.fpsm.shop_editor.title")
//                    )
//            );
        }

    }

    public String getListeners() {
        return this.shopSlot.getListenerNames().toString();
    }

    public String getName() {
        return this.shopSlot.process().getDisplayName().getString();
    }

    public int getAmmo() {
        return this.data.get(0);
    }

    public int getPrice() {
        return this.data.get(1);
    }

    public int getGroupId() {
        return this.data.get(2);
    }

    public void setAmmo(int ammoCount) {
        this.data.set(0, ammoCount);
    }

    public void setPrice(int price) {
        this.data.set(1, price);
    }

    public void setGroupId(int groupId) {
        this.data.set(2, groupId);
    }

    public ContainerData getData() {
        return this.data;
    }
}