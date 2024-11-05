package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.client.ClientTaczTextureData;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.texture.FilePackTexture;
import com.tacz.guns.client.resource.texture.ZipPackTexture;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
import java.util.function.Supplier;

public class ShopDataSlotPacket {
    public final ShopData.ItemType type;
    public final int index;
    public final String name;
    public final ItemStack itemStack;
    public final int cost;
    public ShopDataSlotPacket(ShopData.ItemType type, int index, String name, ItemStack itemStack, int cost){
        this.type = type;
        this.index = index;
        this.name = name;
        this.itemStack =itemStack;
        this.cost = cost;
    }

    public ShopDataSlotPacket(ShopData.ShopSlot shopSlot){
        this.type = shopSlot.type();
        this.index = shopSlot.index();
        this.name = shopSlot.name();
        this.itemStack =shopSlot.itemStack();
        this.cost = shopSlot.cost();
    }

    public static void encode(ShopDataSlotPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeUtf(packet.name);
        buf.writeItemStack(packet.itemStack, false);
        buf.writeInt(packet.cost);
    }

    public static ShopDataSlotPacket decode(FriendlyByteBuf buf) {
        return new ShopDataSlotPacket(
                ShopData.ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readUtf(),
                buf.readItem(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ShopData.ShopSlot slot = new ShopData.ShopSlot(this.index,this.type,this.itemStack,this.cost);
            if(this.itemStack.getItem() instanceof IGun iGun){
                ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(iGun.getGunId(this.itemStack)).orElse(null);
                if (gunIndex != null){
                    slot.setTexture(gunIndex.getHUDTexture());
                    CSGameShopScreen.refreshFlag = true;
                }
            }
           FPSMShop.getInstance().getShopItemData().addShopSlot(slot);
        });
        ctx.get().setPacketHandled(true);
    }
}
