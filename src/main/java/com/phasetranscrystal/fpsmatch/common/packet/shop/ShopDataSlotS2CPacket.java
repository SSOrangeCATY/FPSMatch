package com.phasetranscrystal.fpsmatch.common.packet.shop;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import com.phasetranscrystal.fpsmatch.core.shop.UnknownShopType;
import com.phasetranscrystal.fpsmatch.core.shop.INamedType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

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

    public static void encode(ShopDataSlotS2CPacket packet, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(packet.type.name());
        buf.writeInt(packet.index);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.itemStack);
        buf.writeInt(packet.cost);
        buf.writeInt(packet.boughtCount);
        buf.writeBoolean(packet.locked);
    }

    public static ShopDataSlotS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new ShopDataSlotS2CPacket(
                new UnknownShopType(buf.readUtf()),
                buf.readInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
