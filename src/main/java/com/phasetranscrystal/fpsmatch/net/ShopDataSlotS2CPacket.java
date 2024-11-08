package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopDataSlotS2CPacket {
    public final ShopData.ItemType type;
    public final int index;
    public final String name;
    public final ItemStack itemStack;
    public final int cost;
    public ShopDataSlotS2CPacket(ShopData.ItemType type, int index, String name, ItemStack itemStack, int cost){
        this.type = type;
        this.index = index;
        this.name = name;
        this.itemStack =itemStack;
        this.cost = cost;
    }

    public ShopDataSlotS2CPacket(ShopData.ShopSlot shopSlot,String name){
        this.type = shopSlot.type();
        this.index = shopSlot.index();
        this.name = name;
        this.itemStack =shopSlot.itemStack();
        this.cost = shopSlot.cost();
    }

    public static void encode(ShopDataSlotS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeUtf(packet.name);
        buf.writeItemStack(packet.itemStack, false);
        buf.writeInt(packet.cost);
    }

    public static ShopDataSlotS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopDataSlotS2CPacket(
                ShopData.ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readUtf(),
                buf.readItem(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.currentMap = this.name;
            ShopData.ShopSlot currentSlot = ClientData.clientShopData.getSlotData(this.type,this.index);
            currentSlot.setItemStack(this.itemStack);
            currentSlot.setCost(cost);
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
