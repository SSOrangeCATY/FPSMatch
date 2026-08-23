package com.ptcrys.fpsmatch.common.packet.shop;

import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.shop.INamedType;
import com.ptcrys.fpsmatch.core.shop.ShopAction;
import com.ptcrys.fpsmatch.core.shop.UnknownShopType;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.shop.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class ShopActionC2SPacket {
    public final String name;
    public final INamedType type;
    public final int index;
    public final int action;

    public ShopActionC2SPacket(String mapName, INamedType type, int index, ShopAction action){
        this.name = mapName;
        this.type = type;
        this.index = index;
        this.action = action.ordinal();
    }


    public static void encode(ShopActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name);
        buf.writeUtf(packet.type.name());
        buf.writeInt(packet.index);
        buf.writeInt(packet.action);
    }

    public static ShopActionC2SPacket decode(FriendlyByteBuf buf) {
        return new ShopActionC2SPacket(
                buf.readUtf(),
                new UnknownShopType(buf.readUtf()),
                buf.readInt(),
                ShopAction.values()[buf.readInt()]
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByName(name);
            if(map.isPresent()){
                BaseTeam team = map.get().getMapTeams().getTeamByPlayer(ctx.get().getSender()).orElse(null);
                ShopCapability cap = null;
                if (team != null) {
                    cap = team.getCapabilityMap().get(ShopCapability.class).orElse(null);
                }
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (!map.get().canUseShop(cap, serverPlayer)) {
                    ctx.get().setPacketHandled(true);
                    return;
                }
                cap.getShop().handleButton(serverPlayer, this.type, this.index,ShopAction.values()[this.action]);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
