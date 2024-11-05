package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.net.CSGameSettingsPacket;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CSGameMap extends BaseMap {
    public static final int WINNER_ROUND = 13;
    public static final int PAUSE_TIME = 2400;
    public static final int WINNER_WAITING_TIME = 160;
    public static final int WARM_UP_TIME = 1200;
    private final int waittingTime = 400;
    private int currentPauseTime = 0;
    private final int roundTimeLimit = 115 * 20;
    private int currentRoundTime = 0;
    private boolean isStart = false;
    private boolean isError = false;
    private boolean isPause = false;
    private boolean isWaiting = false;
    private boolean isWarmTime = false;
    private boolean isWaitingWinner = false;
    private final Map<String,Integer> teamScores = new HashMap<>();
    private final ShopData defaultShopData;

    public CSGameMap(ServerLevel serverLevel, String gameType) {
        super(serverLevel, List.of("ct","t"), gameType);
        this.getMapTeams().setTeamPlayerLimit("ct",10);
        this.getMapTeams().setTeamPlayerLimit("t",10);
        this.defaultShopData = this.buildShopData();
    }

    public ShopData buildShopData(){
        ItemStack itemStack = new ItemStack(Items.APPLE);
        int[][] d = new int[][]{
                {650,1000,200,200,200},
                {200,700,600,500,300},
                {1500,1050,1700,2350,1050},
                {1800,2700,3000,1700,4750},
                {200,300,300,400,50}
        };

        Map<ShopData.ItemType, List<ShopData.ShopSlot>> data = new HashMap<>();
        for(ShopData.ItemType c : ShopData.ItemType.values()) {
            List<ShopData.ShopSlot> shopSlots = new ArrayList<>();
            for (int i = 0;i <= 4 ; i++){
                ShopData.ShopSlot shopSlot = new ShopData.ShopSlot(i,c,itemStack,d[c.typeIndex][i]);
                shopSlots.add(shopSlot);
            }
            data.put(c,shopSlots);
        }
        return new ShopData(data);
    }

    public void syncShopDataToClient(ServerPlayer player){
        for (ShopData.ItemType type : ShopData.ItemType.values()){
            List<ShopData.ShopSlot> slots = this.defaultShopData.getShopSlotsByType(type);
            slots.forEach((shopSlot -> {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> player), new ShopDataSlotPacket(shopSlot));
            }));
        }
    }

    @Override
    public void tick() {
        if(isStart){
            if (!checkPauseTime() & !checkWarmUpTime() & !checkWaitingTime()) {
                if(!isRoundTimeEnd()){
                    if(!this.isDebug()) this.checkRoundVictory();
                }else{
                    if(!checkWinnerTime()){
                        this.roundVictory("ct");
                    }else if(this.currentPauseTime >= WINNER_WAITING_TIME){
                        this.startNewRound();
                    }
                }
            }
        }
    }

    public void startGame(){
        AtomicBoolean checkFlag = new AtomicBoolean(true);
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            Player player = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player != null){
                String team = this.getMapTeams().getTeamByPlayer(player);
                if(team == null) checkFlag.set(false);
            }else{
                this.getMapTeams().playerLeave(uuid);
                checkFlag.set(false);
            }
        }));

        if (!checkFlag.get() && !this.isError) return;
        startNewRound();
    }

    public boolean canRestTime(){
        return !this.isPause && !this.isWarmTime && !this.isWaiting && !this.isWaitingWinner;
    }
    public boolean checkPauseTime(){
        if(this.isPause && currentPauseTime < PAUSE_TIME){
            this.currentPauseTime++;
        }else{
            if(this.canRestTime()) currentPauseTime = 0;
            isPause = false;
        }
        return this.isPause;
    }

    public boolean checkWarmUpTime(){
        if(this.isWarmTime && currentPauseTime < WARM_UP_TIME){
            this.currentPauseTime++;
        }else {
            if(this.canRestTime()) currentPauseTime = 0;
            isWarmTime = false;
        }
        return this.isWarmTime;
    }

    public boolean checkWaitingTime(){
        if(this.isWaiting && currentPauseTime < waittingTime){
            this.currentPauseTime++;
        }else {
            if(this.canRestTime()) currentPauseTime = 0;
            isWaiting = false;
        }
        return this.isWaiting;
    }

    public boolean checkWinnerTime(){
        if(this.isWaitingWinner && currentPauseTime < WINNER_WAITING_TIME){
            this.currentPauseTime++;
        }else{
            if(this.canRestTime()) currentPauseTime = 0;
            // 在StartNewRound方法中更改
        }
        return this.isWaitingWinner;
    }


    public void checkRoundVictory(){
        Map<String, List<UUID>> teamsLiving = this.getMapTeams().getTeamsLiving();
        for (String teamName : teamsLiving.keySet()){
            if(teamsLiving.get(teamName).isEmpty()){
                teamsLiving.remove(teamName);
            }
        }

        if(teamsLiving.keySet().size() == 1) {
            String winnerTeam = teamsLiving.keySet().stream().findFirst().get();
            this.roundVictory(winnerTeam);
        }
    }
    public boolean isRoundTimeEnd(){
        if(this.currentRoundTime < this.roundTimeLimit){
            this.currentRoundTime++;
        }
        return this.currentRoundTime >= this.roundTimeLimit;
    }

    private void roundVictory(String teamName) {
        this.isWaitingWinner = true;
        this.teamScores.put(teamName,this.teamScores.getOrDefault(teamName,0) + 1);
    }

    public void startNewRound() {
        this.isStart = true;
        this.isWaiting = true;
        this.isWaitingWinner = false;
        this.getMapTeams().resetLivingPlayers();
        this.getMapTeams().setTeamsSpawnPoints();
        this.cleanupMap();
        // 可以在这里添加更多的逻辑，比如重置得分板、装备武器等
    }

    @Override
    public void victory() {
        // 重置游戏状态
        resetGame();
    }
    @Override
    public boolean victoryGoal() {
        AtomicBoolean isVictory = new AtomicBoolean(false);
        teamScores.values().forEach((integer -> {
            isVictory.set(integer >= WINNER_ROUND);
        }));
        return isVictory.get() && !this.isDebug();
    }

    @Override
    public void cleanupMap() {
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.getMapTeams().setTeamsSpawnPoints();
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player =  this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if(player != null){
                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.ADVENTURE);
                this.clearPlayerInventory(player);
                this.teleportPlayerToReSpawnPoint(player);
            }
        }));
    }

    public void teleportPlayerToReSpawnPoint(ServerPlayer player){
        SpawnPointData data = this.getMapTeams().getPlayersSpawnData().get(player.getUUID());
        BlockPos pos = data.getPosition();
        float f = Mth.wrapDegrees(data.getYaw());
        float f1 = Mth.wrapDegrees(data.getPitch());
        player.teleportTo(Objects.requireNonNullElse(this.getServerLevel().getServer().getLevel(data.getDimension()),this.getServerLevel()),pos.getX(),pos.getY(),pos.getZ(),f, f1);
    }

    public void clearPlayerInventory(ServerPlayer player){
        player.getInventory().clearOrCountMatchingItems((p_180029_) -> true, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void resetGame() {
        this.teamScores.clear();
        this.isError = false;
        this.isStart = false;
        this.isWaiting = false;
        this.isWaitingWinner = false;
        this.isWarmTime = false;
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.getMapTeams().reset();
    }

    @Override
    public String getType() {
        return "cs";
    }

    public void syncToClient() {
        CSGameSettingsPacket packet = new CSGameSettingsPacket(this.teamScores.getOrDefault("ct",0),this.teamScores.getOrDefault("t",0), this.currentPauseTime,this.currentRoundTime,this.isDebug(),this.isStart,this.isError,this.isPause,this.isWaiting,this.isWaitingWinner);
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if(player != null){
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> player), packet);
            }
        }));
    }
}
