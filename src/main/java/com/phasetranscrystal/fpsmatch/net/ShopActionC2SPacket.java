package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.ShopAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopActionC2SPacket {
    public final String name;
    public final ItemType type;
    public final int index;
    public final int action;

    public ShopActionC2SPacket(String mapName, ItemType type, int index, ShopAction action){
        this.name = mapName;
        this.type = type;
        this.index = index;
        this.action = action.ordinal();
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
                ItemType.values()[buf.readInt()],
                buf.readInt(),
                ShopAction.values()[buf.readInt()]
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            BaseMap map = FPSMCore.getInstance().getMapByName(name);
            if(map instanceof ShopMap<?> shopMap){
                BaseTeam team = map.getMapTeams().getTeamByPlayer(ctx.get().getSender()).orElse(null);;
                FPSMShop shop = null;
                if (team != null) {
                    shop = shopMap.getShop(team.name).orElse(null);
                }
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (shop == null || serverPlayer == null) {
                    ctx.get().setPacketHandled(true);
                    return;
                }
                shop.handleButton(serverPlayer, this.type, this.index,ShopAction.values()[this.action]);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
