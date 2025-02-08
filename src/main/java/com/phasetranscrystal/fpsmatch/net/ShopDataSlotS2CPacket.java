package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopDataSlotS2CPacket {
    public final ItemType type;
    public final int index;

    public final ItemStack itemStack;
    public final int boughtCount;
    public final int cost;
    public final boolean locked;

    public ShopDataSlotS2CPacket(ItemType type, int index, ItemStack itemStack, int cost,int boughtCount,boolean locked){
        this.type = type;
        this.index = index;
        this.itemStack =itemStack;
        this.cost = cost;
        this.boughtCount = boughtCount;
        this.locked = locked;
    }

    public ShopDataSlotS2CPacket(ItemType type, ShopSlot shopSlot){
        this.type = type;
        this.index = shopSlot.getIndex();
        this.itemStack = shopSlot.process();
        this.cost = shopSlot.getCost();
        this.boughtCount = shopSlot.getBoughtCount();
        this.locked = shopSlot.isLocked();
    }

    public static void encode(ShopDataSlotS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeItemStack(packet.itemStack, false);
        buf.writeInt(packet.cost);
        buf.writeInt(packet.boughtCount);
        buf.writeBoolean(packet.locked);
    }

    public static ShopDataSlotS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopDataSlotS2CPacket(
                ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readItem(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientShopSlot currentSlot = ClientData.getSlotData(this.type,this.index);
            currentSlot.setItemStack(this.itemStack);
            currentSlot.setCost(cost);
            currentSlot.setBoughtCount(boughtCount);
            currentSlot.setLock(locked);

            if(!CSGameShopScreen.refreshFlag){
                CSGameShopScreen.refreshFlag = true;
            }

        });
        ctx.get().setPacketHandled(true);
    }
}
