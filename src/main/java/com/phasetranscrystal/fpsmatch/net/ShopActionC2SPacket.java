package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopActionC2SPacket {
    public static final int ACTION_RETURN = 0;
    public static final int ACTION_BUY = 1;

    public final String name;

    public final ShopData.ItemType type;
    public final int index;
    public final int action;

    public ShopActionC2SPacket(String mapName, ShopData.ItemType type, int index, int action){
        this.name = mapName;
        this.type = type;
        this.index = index;
        this.action = action;
    }

    public ShopActionC2SPacket(String mapName, ShopData.ShopSlot shopSlot, int action){
        this.name = mapName;
        this.type = shopSlot.type();
        this.index = shopSlot.index();
        this.action = action;
    }

    public static void encode(ShopActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name);
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeInt(packet.action);
    }

    public static ShopActionC2SPacket decode(FriendlyByteBuf buf) {
        return new ShopActionC2SPacket(
                buf.readUtf(),
                ShopData.ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            BaseMap map = FPSMCore.getInstance().getMapByName(name);
            if(map instanceof ShopMap shopMap){
                FPSMShop shop = shopMap.getShop();
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (shop == null || serverPlayer == null) {
                    ctx.get().setPacketHandled(true);
                    return;
                }

                if (this.action == 0){
                    shop.handleReturnButton(serverPlayer, this.type, this.index);
                }

                if(this.action == 1){
                    shop.handleShopButton(serverPlayer, this.type, this.index);
                }

                if(this.action == 2){
                    shop.resetSlot(serverPlayer,this.type, this.index);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
