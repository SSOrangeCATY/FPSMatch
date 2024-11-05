package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopDataSlotPacket {
    public final ShopData.ItemType type;
    public final int index;
    public final String name;
    public final ResourceLocation texture;
    public final int cost;
    public ShopDataSlotPacket(ShopData.ItemType type, int index, String name, ResourceLocation texture, int cost){
        this.type = type;
        this.index = index;
        this.name = name;
        this.texture =texture;
        this.cost = cost;
    }

    public ShopDataSlotPacket(ShopData.ShopSlot shopSlot){
        this.type = shopSlot.type();
        this.index = shopSlot.index();
        this.name = shopSlot.name();
        this.texture =shopSlot.getTexture();
        this.cost = shopSlot.cost();
    }

    public static void encode(ShopDataSlotPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeUtf(packet.name);
        buf.writeResourceLocation(packet.texture);
        buf.writeInt(packet.cost);
    }

    public static ShopDataSlotPacket decode(FriendlyByteBuf buf) {
        return new ShopDataSlotPacket(
                ShopData.ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readUtf(),
                buf.readResourceLocation(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
           FPSMShop.getInstance().getShopItemData().addShopSlot(new ShopData.ShopSlot(this.index,this.type,this.name,this.cost,this.texture));
        });
        ctx.get().setPacketHandled(true);
    }
}
