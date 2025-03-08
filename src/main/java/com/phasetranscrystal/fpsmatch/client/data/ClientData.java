package com.phasetranscrystal.fpsmatch.client.data;

import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.Config;
import com.phasetranscrystal.fpsmatch.client.screen.hud.CSGameHud;
import com.phasetranscrystal.fpsmatch.client.screen.hud.MVPHud;
import com.phasetranscrystal.fpsmatch.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ClientData {
    public static String currentMap = "fpsm_none";
    public static String currentGameType = "none";
    public static String currentTeam = "ct";
    public static boolean currentMapSupportShop = true;
    public static final Map<ItemType, List<ClientShopSlot>> clientShopData = getDefaultShopSlotData();
    public static int nextRoundMoney = 0;
    public static final Map<UUID, Pair<String,TabData>> tabData = new HashMap<>();
    public static final Map<UUID,Integer> playerMoney = new HashMap<>();
    public static int cTWinnerRounds = 0;
    public static int tWinnerRounds = 0;
    public static int pauseTime = 0;
    public static int roundTime = 0;
    public static boolean isDebug = false;
    public static boolean isStart = false;
    public static boolean isError = false;
    public static boolean isPause = false;
    public static boolean isWaiting = false;
    public static boolean isWarmTime = false;
    public static boolean isWaitingWinner = false;
    public static boolean canOpenShop = false;
    public static float dismantleBombProgress = 0;
    public static boolean customTab = true;

    public static int getMoney(){
        return playerMoney.getOrDefault(Minecraft.getInstance().player.getUUID(),0);
    }
    public static Map<ItemType, List<ClientShopSlot>> getDefaultShopSlotData(){
        Map<ItemType, List<ClientShopSlot>> data = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            List<ClientShopSlot> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(ClientShopSlot.getDefault());
            }
            data.put(type, list);
        }

        return data;
    }

    public static void resetShopData(){
        clientShopData.clear();
        clientShopData.putAll(getDefaultShopSlotData());
    }

    public static ClientShopSlot getSlotData(ItemType type,int index){
        return clientShopData.get(type).get(index);
    }

    public static void handleLoginMessage(){
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if(Config.client.showLogin.get()){
                player.displayClientMessage(Component.translatable("fpsm.screen.scale.warm").withStyle(ChatFormatting.GRAY), false);
                player.displayClientMessage(Component.translatable("fpsm.screen.scale.warm.tips").withStyle(ChatFormatting.GRAY), false);
                player.displayClientMessage(Component.translatable("fpsm.login.message.closeable").withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    public static void removePlayerFromTab(UUID uuid){
        tabData.remove(uuid);
    }

    public static void reset() {
        MVPHud.INSTANCE.resetAnimation();
        currentMap = "fpsm_none";
        currentGameType = "none";
        currentMapSupportShop = true;
        CSGameHud.INSTANCE.stopKillAnim();
        resetShopData();
        tabData.clear();
        playerMoney.clear();
        cTWinnerRounds = 0;
        tWinnerRounds = 0;
        pauseTime = 0;
        roundTime = 0;
        isDebug = false;
        isStart = false;
        isError = false;
        isPause = false;
        isWaiting = false;
        isWarmTime = false;
        isWaitingWinner = false;
        nextRoundMoney = 0;
        canOpenShop = false;
        dismantleBombProgress = 0;
    }

    public static int getNextRoundMinMoney() {
        return nextRoundMoney;
    }

    @Nullable
    public static TabData getTabDataByUUID(UUID uuid){
        if(ClientData.tabData.containsKey(uuid)){
            return ClientData.tabData.get(uuid).getSecond();
        }
        return null;
    }

    @Nullable
    public static String getTeamByUUID(UUID uuid){
        if(ClientData.tabData.containsKey(uuid)){
            return ClientData.tabData.get(uuid).getFirst();
        }
        return null;
    }

    public static int getLivingWithTeam(String team){
        AtomicReference<Integer> living = new AtomicReference<>(0);
        ClientData.tabData.values().forEach((pair)->{
            if(pair.getFirst().equals(team) && pair.getSecond().isLiving()){
                living.getAndSet(living.get() + 1);
            }
        });
        return living.get();
    }

}
