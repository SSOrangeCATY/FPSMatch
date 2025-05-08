package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// UpdateShopSlotPacket.java
public class UpdateShopSlotPacket {
    private final ItemType type;
    private final int slotIndex;
    ItemStack stack;
    int cost;
    int groupId;
    int ammo = -1;

    public UpdateShopSlotPacket(ItemType type, int slotIndex, ClientShopSlot slot) {
        this.type = type;
        this.slotIndex = slotIndex;
        this.stack = slot.itemStack();
        this.cost = slot.cost();
        this.groupId = slot.groupId();
    }
    public UpdateShopSlotPacket(ItemType type, int slotIndex, ClientShopSlot slot,int ammo) {
        this.type = type;
        this.slotIndex = slotIndex;
        this.stack = slot.itemStack();
        this.cost = slot.cost();
        this.groupId = slot.groupId();
        this.ammo = ammo;
    }

    public UpdateShopSlotPacket(ItemType type, int slotIndex, ItemStack stack, int cost, int groupId) {
        this.type = type;
        this.slotIndex = slotIndex;
        this.stack = stack;
        this.cost = cost;
        this.groupId = groupId;
    }
    public UpdateShopSlotPacket(ItemType type, int slotIndex, ItemStack stack, int cost, int groupId, int ammo) {
        this.type = type;
        this.slotIndex = slotIndex;
        this.stack = stack;
        this.cost = cost;
        this.groupId = groupId;
        this.ammo = ammo;
    }

    public static void encode(UpdateShopSlotPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.type.ordinal());
        buffer.writeInt(msg.slotIndex);
        buffer.writeItem(msg.stack);
        buffer.writeInt(msg.cost);
        buffer.writeInt(msg.groupId);
        buffer.writeInt(msg.ammo);
    }

    public static UpdateShopSlotPacket decode(FriendlyByteBuf buffer) {
        ItemType type = ItemType.values()[buffer.readInt()];
        int slotIndex = buffer.readInt();
        ItemStack stack = buffer.readItem();
        int cost = buffer.readInt();
        int groupId = buffer.readInt();
        int ammo = buffer.readInt();
        return new UpdateShopSlotPacket(type, slotIndex, stack, cost, groupId, ammo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if(map instanceof ShopMap<?> shop) {
                BaseTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                if (team != null) {
                    FPSMShop playerShop = shop.getShop(team.name);
                    if(playerShop != null){
                        if(stack.getItem() instanceof IGun iGun && ammo != -1){
                            FPSMUtil.setTotalDummyAmmo(stack,iGun,ammo);
                        }
                        playerShop.setDefaultShopDataItemStack(type, slotIndex,stack);
                        playerShop.setDefaultShopDataCost(type, slotIndex, cost);
                        playerShop.setDefaultShopDataGroupId(type, slotIndex, groupId);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}