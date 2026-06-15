package com.phasetranscrystal.fpsmatch.common.client.screen;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.phasetranscrystal.fpsmatch.common.capability.team.ShopCapability;
import com.phasetranscrystal.fpsmatch.common.mapselect.MapRoomQueryService;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.util.FPSMCodec;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.phasetranscrystal.fpsmatch.compat.gun.GunCompatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.List;
import java.util.Optional;


public class EditShopSlotMenu extends AbstractContainerMenu {
    private static final int ID_MAX_LENGTH = 128;

    private final ContainerData data;
    private final ItemStackHandler itemHandler;
    private final ShopSlot shopSlot;
    private final String gameType;
    private final String mapName;
    private final String teamName;
    private final String shopType;
    private final int slotNum;

    public EditShopSlotMenu(int id, Inventory playerInventory, ShopSlot shopSlot, String gameType, String mapName, String teamName, String shopType, int slotNum) {
        this(id, playerInventory, new ItemStackHandler(1), new SimpleContainerData(3), shopSlot, gameType, mapName, teamName, shopType, slotNum);
    }

    public EditShopSlotMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(
                id,
                playerInventory,
                FPSMCodec.decodeFromJson(ShopSlot.CODEC, new Gson().fromJson(buf.readUtf(), JsonElement.class)),
                buf.readUtf(ID_MAX_LENGTH),
                buf.readUtf(ID_MAX_LENGTH),
                buf.readUtf(ID_MAX_LENGTH),
                buf.readUtf(ID_MAX_LENGTH),
                buf.readInt()
        );
    }

    public EditShopSlotMenu(int id, Inventory playerInventory, ItemStackHandler handler, ContainerData data, ShopSlot shopSlot, String gameType, String mapName, String teamName, String shopType, int slotNum) {
        super(VanillaGuiRegister.EDIT_SHOP_SLOT_MENU.get(), id);
        this.itemHandler = handler;
        this.data = data;
        this.shopSlot = shopSlot;
        this.gameType = gameType;
        this.mapName = mapName;
        this.teamName = teamName;
        this.shopType = shopType;
        this.slotNum = slotNum;
        this.setAmmo(shopSlot.getAmmoCount());
        this.setPrice(shopSlot.getDefaultCost());
        this.setGroupId(shopSlot.getGroupId());
        this.itemHandler.setStackInSlot(0, this.shopSlot.process());
        this.addSlot(new SlotItemHandler(itemHandler, 0, 20, 20));

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

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player pPlayer) {
        super.removed(pPlayer);
    }

    public void saveData(ServerPlayer serverPlayer) {
        Optional<FPSMShop<?>> resolvedShop = MapRoomQueryService.findMap(gameType, mapName)
                .flatMap(this::resolveTeam)
                .flatMap(ShopCapability::getShop);
        if (resolvedShop.isEmpty()) {
            return;
        }

        FPSMShop<?> shop = resolvedShop.get();
        shopSlot.setItemSupplier(() -> itemHandler.getStackInSlot(0));
        ItemStack slotStack = shopSlot.process();
        shopSlot.setDefaultCost(this.getPrice());
        shopSlot.setGroupId(this.getGroupId());
        if (GunCompatManager.isGun(slotStack)) {
            FPSMUtil.setTotalDummyAmmo(slotStack, GunCompatManager.findProvider(slotStack), this.getAmmo());
        }
        shop.replaceDefaultShopData(shopType, slotNum, shopSlot);
        shop.syncShopData();
    }

    private Optional<ServerTeam> resolveTeam(BaseMap map) {
        return map.getMapTeams().getTeamByName(teamName);
    }

    public List<String> getListeners() {
        return this.shopSlot.getListenerNames();
    }

    public boolean isGun(){
        return GunCompatManager.isGun(this.slots.get(0).getItem());
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

    public String getGameType() {
        return gameType;
    }

    public String getMapName() {
        return mapName;
    }

    public String getTeamName() {
        return teamName;
    }

    public ContainerData getData() {
        return this.data;
    }
}
