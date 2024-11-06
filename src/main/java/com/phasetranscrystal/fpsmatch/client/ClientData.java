package com.phasetranscrystal.fpsmatch.client;

import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;

import java.util.*;

public class ClientData {
    public static String currentMap = "error";
    public static final ShopData clientShopData = new ShopData(ShopData.getDefaultShopItemData(false));
    public static int money = 10000;
    public static final Map<UUID, TabData> tabData = new HashMap<>();

    public static ShopData.ShopSlot getSlotData(ShopData.ItemType type, int index) {
        return clientShopData.getSlotData(type,index);
    }

    public static void overWriteShopSlot(ShopData.ShopSlot slot){
        clientShopData.addShopSlot(slot);
    }

    public static int getMoney(){
        return money;
    }

}
