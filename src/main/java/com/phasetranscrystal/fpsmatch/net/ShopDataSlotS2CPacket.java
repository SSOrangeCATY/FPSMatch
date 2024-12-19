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
    public final String name;
    public final ItemStack itemStack;

    public final int boughtCount;
    public final int cost;
    public ShopDataSlotS2CPacket(ItemType type, int index, String name, ItemStack itemStack, int cost,int boughtCount){
        this.type = type;
        this.index = index;
        this.name = name;
        this.itemStack =itemStack;
        this.cost = cost;
        this.boughtCount = boughtCount;
    }

    public ShopDataSlotS2CPacket(ItemType type, ShopSlot shopSlot, String name){
        this.type = type;
        this.index = shopSlot.getIndex();
        this.name = name;
        this.itemStack = shopSlot.process();
        this.cost = shopSlot.getCost();
        this.boughtCount = shopSlot.getBoughtCount();
    }

    public static void encode(ShopDataSlotS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeUtf(packet.name);
        buf.writeItemStack(packet.itemStack, false);
        buf.writeInt(packet.cost);
        buf.writeInt(packet.boughtCount);
    }

    public static ShopDataSlotS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopDataSlotS2CPacket(
                ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readUtf(),
                buf.readItem(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.currentMap = this.name;
            ClientShopSlot currentSlot = ClientData.getSlotData(this.type,this.index);
            currentSlot.setItemStack(this.itemStack);
            currentSlot.setCost(cost);
            currentSlot.setBoughtCount(boughtCount);

            if(this.itemStack.getItem() instanceof IGun iGun){
                ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(iGun.getGunId(this.itemStack)).orElse(null);
                if (gunIndex != null){
                    currentSlot.setTexture(gunIndex.getHUDTexture());
                    CSGameShopScreen.refreshFlag = true;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
