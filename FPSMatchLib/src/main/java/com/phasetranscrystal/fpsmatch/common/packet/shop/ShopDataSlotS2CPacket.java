package com.phasetranscrystal.fpsmatch.common.packet.shop;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.shop.UnknownShopType;
import com.phasetranscrystal.fpsmatch.core.shop.INamedType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopDataSlotS2CPacket {
    public final INamedType type;
    public final int index;
    public final ItemStack itemStack;
    public final int boughtCount;
    public final int cost;
    public final boolean locked;

    public ShopDataSlotS2CPacket(INamedType type, int index, ItemStack itemStack, int cost,int boughtCount,boolean locked){
        this.type = type;
        this.index = index;
        this.itemStack =itemStack;
        this.cost = cost;
        this.boughtCount = boughtCount;
        this.locked = locked;
    }

    public ShopDataSlotS2CPacket(INamedType type, ShopSlot shopSlot){
        this.type = type;
        this.index = shopSlot.getIndex();
        this.itemStack = shopSlot.process();
        this.cost = shopSlot.getCost();
        this.boughtCount = shopSlot.getBoughtCount();
        this.locked = shopSlot.isLocked();
    }

    public static void encode(ShopDataSlotS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.type.name());
        buf.writeInt(packet.index);
        buf.writeItemStack(packet.itemStack, false);
        buf.writeInt(packet.cost);
        buf.writeInt(packet.boughtCount);
        buf.writeBoolean(packet.locked);
    }

    public static ShopDataSlotS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopDataSlotS2CPacket(
                new UnknownShopType(buf.readUtf()),
                buf.readInt(),
                buf.readItem(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 使用改进后的方法
            ClientShopSlot currentSlot = FPSMClient.getGlobalData().getSlotData(this.type.name(), this.index);

            // 安全设置属性
            if (currentSlot != null) {  // 假设ClientShopSlot.empty()可能返回null
                currentSlot.setItemStack(this.itemStack);
                currentSlot.setCost(this.cost);
                currentSlot.setBoughtCount(this.boughtCount);
                currentSlot.setLock(this.locked);
            } else {
                FPSMatch.LOGGER.error("Failed to update slot data for {} at index {}", this.type.name(), this.index);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
