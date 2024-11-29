package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


// 当发送这个包给客户端代表操作执行成功 客户端更新相应的反馈即可
public class ShopActionS2CPacket {
    public static final int ACTION_RETURN = 0;
    public static final int ACTION_BUY = 1;

    public final String name;

    public final ShopData.ItemType type;
    public final int index;
    public final int action;
    public final int money;

    public ShopActionS2CPacket(String mapName, ShopData.ItemType type, int index, int action, int money){
        this.name = mapName;
        this.type = type;
        this.index = index;
        this.action = action;
        this.money = money;
    }

    public ShopActionS2CPacket(String mapName, ShopData.ShopSlot shopSlot, int action, int money){
        this.name = mapName;
        this.type = shopSlot.type();
        this.index = shopSlot.index();
        this.action = action;
        this.money = money;
    }

    public static void encode(ShopActionS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name);
        buf.writeInt(packet.type.ordinal());
        buf.writeInt(packet.index);
        buf.writeInt(packet.action);
        buf.writeInt(packet.money);
    }

    public static ShopActionS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopActionS2CPacket(
                buf.readUtf(),
                ShopData.ItemType.values()[buf.readInt()],
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if(!name.equals("null")){
                ClientData.currentMap = name;
            }
            ShopData.ShopSlot slot = ClientData.clientShopData.getSlotData(type,index);
            ClientData.clientShopData.setMoney(money);

            if(action == ACTION_BUY) {
                slot.bought();
            }

            if(action == ACTION_RETURN){
                slot.returnGoods();
            }

            if(action == 2){
                while (slot.canReturn()){
                    slot.returnGoods();
                }
            }

            if(action == 3){
                boolean canBuyTwo = type == ShopData.ItemType.THROWABLE && index == 0;
                if (slot.boughtCount() < money){
                    for (int i = 0; i <= 2; i++){
                        if(canBuyTwo){
                            slot.bought(i != 2);
                        }else {
                            slot.bought(false);
                        }
                    }
                }else{
                    while (slot.boughtCount() > money){
                        slot.returnGoods();
                    }
                }
            }

            CSGameShopScreen.refreshFlag = true;
        });
        ctx.get().setPacketHandled(true);
    }

}
