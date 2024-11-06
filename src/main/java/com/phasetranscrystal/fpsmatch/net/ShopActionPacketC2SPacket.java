package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.client.ClientData;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ShopActionPacketC2SPacket {
    public static final int ACTION_RETURN = 0;
    public static final int ACTION_BUY = 1;

    public final String name;

    public final ShopData.ItemType type;
    public final int index;
    public final int action;

    public ShopActionPacketC2SPacket(String mapName, ShopData.ItemType type, int index, int action){
        this.name = mapName;
        this.type = type;
        this.index = index;
        this.action = action;
    }

    public ShopActionPacketC2SPacket(String mapName, ShopData.ShopSlot shopSlot,int action){
        this.name = mapName;
        this.type = shopSlot.type();
        this.index = shopSlot.index();
        this.action = action;
    }

    public static void encode(ShopActionPacketC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name);
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeInt(packet.action);
    }

    public static ShopActionPacketC2SPacket decode(FriendlyByteBuf buf) {
        return new ShopActionPacketC2SPacket(
                buf.readUtf(),
                ShopData.ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            FPSMShop shop =  FPSMShop.getShopByMapName(this.name);
            ServerPlayer serverPlayer = ctx.get().getSender();
            if (shop == null || serverPlayer == null) return;

            if (this.action == 0){
                shop.handleReturnButton(serverPlayer.getUUID(), this.type, this.index);
            }

            if(this.action == 1){
                shop.handleShopButton(serverPlayer.getUUID(), this.type, this.index);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
