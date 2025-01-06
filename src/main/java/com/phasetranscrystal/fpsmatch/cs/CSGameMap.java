package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.*;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.event.PlayerKillOnMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import com.phasetranscrystal.fpsmatch.item.CompositionC4;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.net.BombDemolitionProgressS2CPacket;
import com.phasetranscrystal.fpsmatch.net.CSGameSettingsS2CPacket;
import com.phasetranscrystal.fpsmatch.net.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopStatesS2CPacket;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.IGun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CSGameMap extends BaseMap implements BlastModeMap<CSGameMap> , ShopMap , GiveStartKitsMap<CSGameMap> {
    private static final Map<String, BiConsumer<CSGameMap,ServerPlayer>> COMMANDS = registerCommands();
    private static final Map<String, Consumer<CSGameMap>> VOTE_ACTION = registerVoteAction();
    public static final int WINNER_ROUND = 13; // 13回合
    public static final int PAUSE_TIME = 1200; // 60秒
    public static final int WINNER_WAITING_TIME = 160;
    public static final int WARM_UP_TIME = 1200;
    private int waitingTime = 300;
    private int currentPauseTime = 0;
    private final int roundTimeLimit = 115 * 20;
    private int currentRoundTime = 0;
    private boolean isError = false;
    private boolean isPause = false;
    private boolean isWaiting = false;
    private boolean isWarmTime = false;
    private boolean isWaitingWinner = false;
    private boolean isShopLocked = false;
    private int isBlasting = 0; // 是否放置炸弹 0 = 未放置 | 1 = 已放置 | 2 = 已拆除
    private boolean isExploded = false; // 炸弹是否爆炸
    private final List<AreaData> bombAreaData = new ArrayList<>();
    private String blastTeam;
    private final FPSMShop shop;
    private final Map<String,List<ItemStack>> startKits = new HashMap<>();
    private boolean isOvertime = false;
    private int overtimeRounds = 6;
    private int overtimeWins = 4;
    private VoteObj voteObj = null;
    private SpawnPointData matchEndTeleportPoint = null;

    public static Map<String, BiConsumer<CSGameMap,ServerPlayer>> registerCommands(){
        Map<String, BiConsumer<CSGameMap,ServerPlayer>> commands = new HashMap<>();
        commands.put("p", CSGameMap::setPauseState);
        commands.put("pause", CSGameMap::setPauseState);
        commands.put("unpause", CSGameMap::startUnpauseVote);
        commands.put("up", CSGameMap::startUnpauseVote);
        commands.put("agree",CSGameMap::handleAgreeCommand);
        commands.put("a",CSGameMap::handleAgreeCommand);
        commands.put("disagree",CSGameMap::handleDisagreeCommand);
        commands.put("da",CSGameMap::handleDisagreeCommand);
        commands.put("start",CSGameMap::handleStartCommand);
        commands.put("reset",CSGameMap::handleResetCommand);
        return commands;
    }

    private void handleResetCommand(ServerPlayer serverPlayer) {
        if(this.voteObj == null && this.isStart){
            this.startVote("reset",Component.translatable("fpsm.map.vote.message",serverPlayer.getDisplayName(),Component.translatable("fpsm.cs.reset")),20,1f);
            this.voteObj.addAgree(serverPlayer);
        } else if (this.voteObj != null) {
            Component translation = Component.translatable("fpsm.cs." + this.voteObj.getVoteTitle());
            serverPlayer.displayClientMessage(Component.translatable("fpsm.map.vote.fail.alreadyHasVote", translation).withStyle(ChatFormatting.RED),false);
        }
    }

    private void handleStartCommand(ServerPlayer serverPlayer) {
        if((!this.isStart && this.voteObj == null) || (!this.isStart && !this.voteObj.getVoteTitle().equals("start"))){
            this.startVote("start",Component.translatable("fpsm.map.vote.message",serverPlayer.getDisplayName(),Component.translatable("fpsm.cs.start")),20,1f);
            this.voteObj.addAgree(serverPlayer);
        }
    }

    public static Map<String, Consumer<CSGameMap>> registerVoteAction(){
        Map<String, Consumer<CSGameMap>> commands = new HashMap<>();
        commands.put("overtime",CSGameMap::startOvertime);
        commands.put("unpause", CSGameMap::setUnPauseState);
        commands.put("reset", CSGameMap::resetGame);
        commands.put("start",CSGameMap::startGame);
        return commands;
    }

    public CSGameMap(ServerLevel serverLevel,String mapName,AreaData areaData) {
        super(serverLevel,mapName,areaData);
        this.shop = new FPSMShop(mapName,800);
    }

    public Map<String,Integer> getTeams(){
        Map<String,Integer> teams = new HashMap<>();
        teams.put("ct",16);
        teams.put("t",16);
        this.setBlastTeam("t");
        return teams;
    }


    public void startVote(String title,Component message,int second,float playerPercent){
        if(this.voteObj == null){
            this.voteObj = new VoteObj(title,message,second,playerPercent);
            this.sendAllPlayerMessage(message,false);
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.help").withStyle(ChatFormatting.GREEN),false);
        }
    }

    @Override
    public FPSMShop getShop() {
        return shop;
    }

    @Override
    public void tick() {
        if(isStart && !checkPauseTime()){
            // 暂停 / 热身 / 回合开始前的等待时间
            if (!checkWarmUpTime() & !checkWaitingTime()) {
                if(!isRoundTimeEnd()){
                    if(!this.isDebug()){
                        boolean flag = this.getMapTeams().getJoinedPlayers().size() != 1;
                        switch (this.isBlasting()){
                            case 1 : this.checkBlastingVictory(); break;
                            case 2 : if(!isWaitingWinner) this.roundVictory("ct",WinnerReason.DEFUSE_BOMB); break;
                            default : if(flag) this.checkRoundVictory(); break;
                        }

                        // 回合结束等待时间
                        if(this.isWaitingWinner){
                            checkWinnerTime();

                            if(this.currentPauseTime >= WINNER_WAITING_TIME){
                                this.startNewRound();
                            }
                        }
                    }
                }else{
                    if(!checkWinnerTime()){
                        this.roundVictory("ct",WinnerReason.TIME_OUT);
                    }else if(this.currentPauseTime >= WINNER_WAITING_TIME){
                        this.startNewRound();
                    }
                }
            }
        }

        this.voteLogic();
        if(this.isStart){
            this.checkErrorPlayerTeam();
        }
    }

    private void voteLogic() {
        if(this.voteObj != null){
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.timer",(this.voteObj.getEndVoteTimer() - System.currentTimeMillis()) / 1000).withStyle(ChatFormatting.DARK_AQUA),true);
            int joinedPlayer = this.getMapTeams().getJoinedPlayers().size();
            AtomicInteger count = new AtomicInteger();
            this.voteObj.voteResult.values().forEach(aBoolean -> {
                if (aBoolean){
                    count.addAndGet(1);
                }
            });
            boolean accept = (float) count.get() / joinedPlayer >= this.voteObj.getPlayerPercent();
            if(this.voteObj.checkVoteIsOverTime() || this.voteObj.voteResult.keySet().size() == joinedPlayer || accept){
                Component translation = Component.translatable("fpsm.cs." + this.voteObj.getVoteTitle());
                if(accept){
                    if(VOTE_ACTION.containsKey(this.voteObj.getVoteTitle())){
                        this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.success",translation).withStyle(ChatFormatting.GREEN),false);
                        VOTE_ACTION.get(this.voteObj.getVoteTitle()).accept(this);
                    }
                }else{
                    this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.fail",translation).withStyle(ChatFormatting.RED),false);
                    List<UUID> players = this.getMapTeams().getJoinedPlayers();
                    this.voteObj.voteResult.keySet().forEach(players::remove);
                    players.forEach(uuid -> {
                        Component name = this.getMapTeams().playerName.getOrDefault(uuid,Component.literal(uuid.toString()));
                        this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.disagree",name).withStyle(ChatFormatting.RED),false);
                    });
                }
                this.voteObj = null;
            }
        }
    }

    private void checkErrorPlayerTeam() {
        if(!this.getMapTeams().getUnableToSwitch().isEmpty()){
            this.getMapTeams().getUnableToSwitch().forEach((team,uuidList)->{
                uuidList.forEach(uuid -> {
                    ServerPlayer serverPlayer = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
                    if(serverPlayer != null){
                        BaseTeam baseTeam = this.getMapTeams().getTeamByName(team);
                        if(baseTeam == null) return;
                        serverPlayer.getScoreboard().addPlayerToTeam(serverPlayer.getScoreboardName(), baseTeam.getPlayerTeam());
                        this.clearPlayerInventory(serverPlayer);
                        this.givePlayerKits(serverPlayer);
                        this.getShop().clearPlayerShopData(serverPlayer.getUUID());
                        serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.team.switch").withStyle(ChatFormatting.GREEN)));
                        uuidList.remove(uuid);
                        if(uuidList.isEmpty()){
                            this.getMapTeams().getUnableToSwitch().remove(team);
                        }
                    }
                });
            });
        }
    }

    public void startGame(){
        this.getMapTeams().setTeamNameColor(this,"ct",ChatFormatting.BLUE);
        this.getMapTeams().setTeamNameColor(this,"t",ChatFormatting.YELLOW);
        AtomicBoolean checkFlag = new AtomicBoolean(true);
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player != null){
                BaseTeam team = this.getMapTeams().getTeamByPlayer(player);
                if(team == null) checkFlag.set(false);
            }else{
                checkFlag.set(false);
            }
        }));

        if (!checkFlag.get() && !this.isError) return;
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,null);
        this.getServerLevel().getServer().setDifficulty(Difficulty.HARD,true);
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> this.setPlayerMoney(uuid,800)));
        this.isStart = true;
        this.isWaiting = true;
        this.isWaitingWinner = false;
        this.setBlasting(0);
        this.setExploded(false);
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.isShopLocked = false;
        this.getMapTeams().setTeamsSpawnPoints();
        this.getMapTeams().resetLivingPlayers();
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer serverPlayer = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if(serverPlayer != null){
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), new ShopStatesS2CPacket(true));
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.SATURATION,-1,2,false,false,false));
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombDemolitionProgressS2CPacket(0));
                serverPlayer.heal(serverPlayer.getMaxHealth());
                serverPlayer.setGameMode(GameType.ADVENTURE);
                this.clearPlayerInventory(serverPlayer);
                this.teleportPlayerToReSpawnPoint(serverPlayer);
            }
        }));
        this.giveAllPlayersKits();
        this.giveBlastTeamBomb();
        super.cleanupMap();
        this.getShop().clearPlayerShopData();
        this.getShop().syncShopData();
    }

    public boolean canRestTime(){
        return !this.isPause && !this.isWarmTime && !this.isWaiting && !this.isWaitingWinner;
    }
    public boolean checkPauseTime(){
        if(this.isPause && currentPauseTime < PAUSE_TIME){
            this.currentPauseTime++;
        }else{
            if(this.isPause) {
                currentPauseTime = 0;
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.done").withStyle(ChatFormatting.GOLD),false);
            }
            isPause = false;
        }
        return this.isPause;
    }

    public boolean checkWarmUpTime(){
        if(this.isWarmTime && currentPauseTime < WARM_UP_TIME){
            this.currentPauseTime++;
        }else {
            if(this.canRestTime()) {
                currentPauseTime = 0;
            }
            isWarmTime = false;
        }
        return this.isWarmTime;
    }

    public boolean checkWaitingTime(){
        if(this.isWaiting && currentPauseTime < waitingTime){
            this.currentPauseTime++;
            boolean b = false;
            Iterator<BaseTeam> teams = this.getMapTeams().getTeams().iterator();
            while (teams.hasNext()){
                BaseTeam baseTeam = teams.next();
                if(!b){
                    b = baseTeam.needPause();
                    if(b){
                        baseTeam.setNeedPause(false);
                    }
                }else{
                    baseTeam.resetPauseIfNeed();
                }
                teams.remove();
            }

            if(b){
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.now").withStyle(ChatFormatting.GOLD),false);
                this.isPause = true;
                this.currentPauseTime = 0;
                this.isWaiting = true;
            }
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
        }
        return this.isWaitingWinner;
    }

    public void checkRoundVictory(){
        if(isWaitingWinner) return;
        Map<String, List<UUID>> teamsLiving = this.getMapTeams().getTeamsLiving();
        if(teamsLiving.size() == 1){
            String winnerTeam = teamsLiving.keySet().stream().findFirst().get();
            this.roundVictory(winnerTeam,WinnerReason.ACED);
        }

        if(teamsLiving.isEmpty()){
            this.roundVictory("ct",WinnerReason.ACED);
        }
    }

    public void checkBlastingVictory(){
        if(isWaitingWinner) return;
        if(this.isExploded()) {
            this.roundVictory("t",WinnerReason.DETONATE_BOMB);
        }else {
            Map<String, List<UUID>> teamsLiving = this.getMapTeams().getTeamsLiving();
            if(teamsLiving.size() == 1){
                String winnerTeam = teamsLiving.keySet().stream().findFirst().get();
                boolean flag = this.checkCanPlacingBombs(Objects.requireNonNull(this.getMapTeams().getTeamByName(winnerTeam)).getFixedName());
                if(flag){
                    this.roundVictory(winnerTeam,WinnerReason.ACED);
                }
            }else if(teamsLiving.isEmpty()){
                this.roundVictory("t",WinnerReason.ACED);
            }
        }
    }

    public boolean isRoundTimeEnd(){
        if(this.isBlasting() > 0){
            this.currentRoundTime = -1;
            return false;
        }
        if(this.currentRoundTime < this.roundTimeLimit){
            this.currentRoundTime++;
        }
        if((this.currentRoundTime >= 200 || this.currentRoundTime == -1 ) && !this.isShopLocked){
            this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
                ServerPlayer serverPlayer = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
                if(serverPlayer != null){
                    FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), new ShopStatesS2CPacket(false));
                }
            }));
            this.isShopLocked = true;
        }
        return this.currentRoundTime >= this.roundTimeLimit;
    }

    public void showWinnerMessage(String winnerTeamName){
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer serverPlayer = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if(serverPlayer != null){
                serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.winner."+winnerTeamName+".round.message").withStyle(winnerTeamName.equals("ct") ? ChatFormatting.DARK_AQUA : ChatFormatting.YELLOW)));
            }
        }));
    }


    /**
     * 处理回合胜利的逻辑
     * 将isWaitingWinner设置成true以倒计时处理startNewRound逻辑
     * @param winnerTeamName 胜利队伍的名称
     * @param reason 胜利原因
     */
    private void roundVictory(String winnerTeamName, WinnerReason reason) {
        // 检查获胜队伍是否存在
        if(this.getMapTeams().checkTeam(winnerTeamName)){
            // 如果已经在等待胜利者，则直接返回
            if(isWaitingWinner) return;
            this.showWinnerMessage(winnerTeamName);
            // 设置为等待胜利者状态
            this.isWaitingWinner = true;
            BaseTeam winnerTeam = this.getMapTeams().getTeamByName(winnerTeamName);
            if(winnerTeam != null){
                int currentScore = winnerTeam.getScores();
                winnerTeam.setScores(currentScore + 1);
            }


            /*
            *
            *         if (!isVictory.get() && !this.isDebug() && !isOvertime) {
            int ctScore = this.getMapTeams().getTeamByName("ct").getScores();
            int tScore = this.getMapTeams().getTeamByName("t").getScores();
            if (ctScore == 12 && tScore == 12) {
                this.startOvertimeVote();
                return false;
            }
        }
            * */
            // 获取胜利队伍和失败队伍列表
            List<BaseTeam> lostTeams = this.getMapTeams().getTeams();
            lostTeams.remove(winnerTeam);

            // 处理胜利经济奖励
            int reward = reason.winMoney;

            if (winnerTeam == null) return;
            // 遍历所有玩家，更新经济
            this.getMapTeams().getJoinedPlayers().forEach(uuid -> {
                // 如果是胜利队伍的玩家
                if (winnerTeam.getPlayerList().contains(uuid)) {
                    this.addPlayerMoney(uuid, reward);
                } else { // 失败队伍的玩家
                    lostTeams.forEach((lostTeam)->{
                        if (lostTeam.getPlayerList().contains(uuid)) {
                            int defaultEconomy = 1400;
                            int compensation = 500;
                            int compensationFactor = lostTeam.getCompensationFactor();
                            // 计算失败补偿
                            int loss = defaultEconomy + compensation * compensationFactor;
                            // 如果玩家没有活着，则给予失败补偿
                            if(!Objects.requireNonNull(lostTeam.getPlayerData(uuid)).getTabData().isLiving()){
                                this.addPlayerMoney(uuid, loss);
                            }
                        }
                    });
                }
            });
            // 检查连败情况
            this.checkLoseStreaks(winnerTeamName);
            // 同步商店金钱数据
            this.getShop().syncShopMoneyData();
        }
    }

    private void checkLoseStreaks(String winnerTeam) {
        // 遍历所有队伍，检查连败情况
        this.getMapTeams().getTeams().forEach(team -> {
            if (team.name.equals(winnerTeam)) {
                // 胜利，连败次数减1
                team.setLoseStreak(Math.max(team.getLoseStreak() - 1,0));
            } else {
                // 失败，连败次数加1
                team.setLoseStreak(team.getLoseStreak() + 1);
            }

            // 更新补偿因数
            int compensationFactor = team.getCompensationFactor();
            if (team.getLoseStreak() > 0) {
                // 连败，补偿因数加1
                compensationFactor = Math.min(compensationFactor + 1, 4);
            }
            team.setCompensationFactor(compensationFactor);
        });
    }

    public void startNewRound() {
        this.isStart = true;
        this.isWaiting = true;
        this.isWaitingWinner = false;
        this.cleanupMap();
        this.getMapTeams().resetLivingPlayers();
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer serverPlayer = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if(serverPlayer != null){
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), new ShopStatesS2CPacket(true));
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.SATURATION,-1,2,false,false,false));
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombDemolitionProgressS2CPacket(0));
                this.teleportPlayerToReSpawnPoint(serverPlayer);
            }
        }));
        this.giveBlastTeamBomb();
        this.getShop().syncShopData();
    }

    @Override
    public void victory() {
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer serverPlayer = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if(serverPlayer != null){
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new FPSMatchStatsResetS2CPacket());
                serverPlayer.removeAllEffects();
            }
        }));
        resetGame();
    }

    @Override
    public boolean victoryGoal() {
        AtomicBoolean isVictory = new AtomicBoolean(false);
        this.getMapTeams().getTeams().forEach((team) -> {
            if (team.getScores() >= (isOvertime ? 999 : WINNER_ROUND)) {
                isVictory.set(true);
                this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
                    ServerPlayer serverPlayer = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
                    if (serverPlayer != null) {
                        serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.winner." + team.name + ".message").withStyle(team.name.equals("ct") ? ChatFormatting.DARK_AQUA : ChatFormatting.YELLOW)));
                    }
                }));
            }
        });
        return isVictory.get() && !this.isDebug();
    }

    public void startOvertimeVote() {
        this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.overtime.vote").withStyle(ChatFormatting.GOLD), false);
        Component translation = Component.translatable("fpsm.cs.overtime");
        this.voteObj = new VoteObj("overtime", Component.translatable("fpsm.map.vote.message","system",translation), 20, 0.5f);
    }

    public void startOvertime() {
        this.isOvertime = true;
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            this.setPlayerMoney(uuid, 10000);
        }));
        this.startNewRound();
    }

    // TODO 重要方法
    @Override
    public void cleanupMap() {
        super.cleanupMap();
        AreaData areaData = this.getMapArea();
        ServerLevel serverLevel = this.getServerLevel();

        serverLevel.getEntitiesOfClass(Entity.class,areaData.getAABB()).forEach(entity -> {
            if(entity instanceof ItemEntity itemEntity){
                itemEntity.discard();
            }
            if(entity instanceof CompositionC4Entity c4){
                c4.discard();
            }
        });

        AtomicInteger atomicInteger = new AtomicInteger(0);
        this.getMapTeams().getTeams().forEach((team)->{
            atomicInteger.addAndGet(team.getScores());
        });

        if(atomicInteger.get() == 12){
            this.getMapTeams().switchAttackAndDefend(this.getServerLevel(),"t","ct");
            this.getShop().clearPlayerShopData();
            this.getShop().syncShopMoneyData();
        }
        this.setBlasting(0);
        this.setExploded(false);
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.isShopLocked = false;
        this.getMapTeams().setTeamsSpawnPoints();
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player =  this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if(player != null){
                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.ADVENTURE);
                if(atomicInteger.get() == 12){
                    this.clearPlayerInventory(player);
                    this.givePlayerKits(player);
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.team.switch").withStyle(ChatFormatting.GREEN)));
                }else{
                    boolean isLiving = Objects.requireNonNull(Objects.requireNonNull(this.getMapTeams().getTeamByPlayer(player)).getPlayerTabData(player.getUUID())).isLiving();
                    if(!isLiving) {
                        this.clearPlayerInventory(player);
                        this.givePlayerKits(player);
                    }else{
                        this.resetGunAmmon();
                    }
                    this.getShop().getPlayerShopData(uuid).lockShopSlots(player);
                }
            }
        }));
        this.getShop().syncShopData();
    }

    public void teleportPlayerToReSpawnPoint(ServerPlayer player){
        BaseTeam team = this.getMapTeams().getTeamByPlayer(player);
        if (team == null) return;
        SpawnPointData data = Objects.requireNonNull(team.getPlayerData(player.getUUID())).getSpawnPointsData();
        TeleportToPoint(player, data);
    }

    public void teleportPlayerToMatchEndPoint(ServerPlayer player){
        if (this.matchEndTeleportPoint == null ) return;
        SpawnPointData data = this.matchEndTeleportPoint;
        TeleportToPoint(player, data);
    }

    private void TeleportToPoint(ServerPlayer player, SpawnPointData data) {
        BlockPos pos = data.getPosition();
        float f = Mth.wrapDegrees(data.getYaw());
        float f1 = Mth.wrapDegrees(data.getPitch());
        if(!Level.isInSpawnableBounds(pos)) return;
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        set.add(RelativeMovement.X_ROT);
        set.add(RelativeMovement.Y_ROT);
        if (player.teleportTo(Objects.requireNonNull(this.getServerLevel().getServer().getLevel(data.getDimension())), pos.getX(),pos.getY(),pos.getZ(), set, f, f1)) {
            label23: {
                if (player.isFallFlying()) {
                    break label23;
                }

                player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                player.setOnGround(true);
            }
        }
    }

    public void giveBlastTeamBomb(){
        BaseTeam team = this.getMapTeams().getTeamByComplexName(this.blastTeam);
        if(team != null){
            Random random = new Random();
            // 随机选择一个玩家作为炸弹携带者
            if(team.getPlayerList().isEmpty()) return;

            team.getPlayerList().forEach((uuid)-> clearPlayerInventory(uuid,(itemStack) -> itemStack.getItem() instanceof CompositionC4));

            UUID uuid = team.getPlayerList().get(random.nextInt(team.getPlayerList().size()));
            if(uuid!= null){
                ServerPlayer player = this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
                if(player!= null){
                    player.addItem(new ItemStack(FPSMItemRegister.C4.get(),1));
                    player.inventoryMenu.broadcastChanges();
                    player.inventoryMenu.slotsChanged(player.getInventory());
                }
            }
        }
    }

    public void clearPlayerInventory(UUID uuid, Predicate<ItemStack> inventoryPredicate){
        Player player = this.getServerLevel().getPlayerByUUID(uuid);
        if(player instanceof ServerPlayer serverPlayer){
            this.clearPlayerInventory(serverPlayer,inventoryPredicate);
        }
    }

    public void clearPlayerInventory(ServerPlayer player, Predicate<ItemStack> predicate){
        player.getInventory().clearOrCountMatchingItems(predicate, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void clearPlayerInventory(ServerPlayer player){
        player.getInventory().clearOrCountMatchingItems((p_180029_) -> true, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    @Override
    public Map<String, List<ItemStack>> getStartKits() {
        return this.startKits;
    }

    public void setPauseState(ServerPlayer player){
        BaseTeam team = this.getMapTeams().getTeamByPlayer(player);
        if(team != null && team.canPause() && this.isStart && !this.isPause){
            team.addPause();
            if(!this.isWaiting){
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.nextRound.success").withStyle(ChatFormatting.GOLD),false);
            }else{
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.success").withStyle(ChatFormatting.GOLD),false);
            }
        }else{
            player.displayClientMessage(Component.translatable("fpsm.map.cs.pause.fail").withStyle(ChatFormatting.RED),false);
        }
    }

    public void setUnPauseState(){
        this.isPause = false;
        this.currentPauseTime = 0;
    }

    private void startUnpauseVote(ServerPlayer serverPlayer) {
        if(this.voteObj == null){
            Component translation = Component.translatable("fpsm.cs.unpause");
            this.startVote("unpause",Component.translatable("fpsm.map.vote.message",serverPlayer.getDisplayName(),translation),15,1f);
            this.voteObj.addAgree(serverPlayer);
        }else{
            Component translation = Component.translatable("fpsm.cs." + this.voteObj.getVoteTitle());
            serverPlayer.displayClientMessage(Component.translatable("fpsm.map.vote.fail.alreadyHasVote", translation).withStyle(ChatFormatting.RED),false);
        }
    }

    public void handleAgreeCommand(ServerPlayer serverPlayer){
        if(this.voteObj != null && !this.voteObj.voteResult.containsKey(serverPlayer.getUUID())){
            this.voteObj.addAgree(serverPlayer);
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.agree",serverPlayer.getDisplayName()).withStyle(ChatFormatting.GREEN),false);
        }
    }

    private void handleDisagreeCommand(ServerPlayer serverPlayer) {
        if(this.voteObj != null && !this.voteObj.voteResult.containsKey(serverPlayer.getUUID())){
            this.voteObj.addDisagree(serverPlayer);
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.disagree",serverPlayer.getDisplayName()).withStyle(ChatFormatting.RED),false);
        }
    }


    public void sendAllPlayerMessage(Component message,boolean actionBar){
        this.getMapTeams().getJoinedPlayers().forEach(uuid -> {
            ServerPlayer serverPlayer = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if(serverPlayer != null){
                serverPlayer.displayClientMessage(message,actionBar);
            }
        });
    }

    public void resetGame() {
        this.cleanupMap();
        this.getShop().clearPlayerShopData();
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player =  this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                this.getServerLevel().getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName());
                player.removeAllEffects();
                this.getShop().syncShopData(player);
                this.resetPlayerClientData(player);
                this.teleportPlayerToMatchEndPoint(player);
            }
        }));
        this.isShopLocked = false;
        this.isError = false;
        this.isStart = false;
        this.isWaiting = false;
        this.isWaitingWinner = false;
        this.isWarmTime = false;
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.isBlasting = 0;
        this.isExploded = false;
        this.getMapTeams().reset();
    }


    public final void setBlastTeam(String team){
        this.blastTeam = this.getGameType()+"_"+this.getMapName()+"_"+team;
    }

    public boolean checkCanPlacingBombs(String team){
        if(this.blastTeam == null) return false;
        return this.blastTeam.equals(team);
    }

    public boolean checkPlayerIsInBombArea(Player player){
        AtomicBoolean a = new AtomicBoolean(false);
        this.bombAreaData.forEach(area->{
            if(!a.get()) a.set(area.isPlayerInArea(player));
        });
        return a.get();
    }

    @Override
    public @NotNull CSGameMap getMap() {
        return this;
    }

    @Override
    public List<ItemStack> getKits(BaseTeam team) {
        List<ItemStack> itemStacks = new ArrayList<>();
        this.startKits.getOrDefault(team.getFixedName(),new ArrayList<>()).forEach((itemStack) -> itemStacks.add(itemStack.copy()));
        return itemStacks;
    }

    @Override
    public void addKits(BaseTeam team, ItemStack itemStack) {
        this.startKits.computeIfAbsent(team.getFixedName(), t -> new ArrayList<>()).add(itemStack);
    }

    @Override
    public void clearTeamKits(BaseTeam team){
        if(this.startKits.containsKey(team.getFixedName())){
            this.startKits.get(team.getFixedName()).clear();
        }
    }

    @Override
    public void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        kits.forEach((s, list) -> list.forEach((itemStack) -> {
            if(itemStack.getItem() instanceof IGun iGun){
                FPSMUtil.fixGunItem(itemStack, iGun);
            }
        }));

        this.startKits.clear();
        this.startKits.putAll(kits);
    }


    @Override
    public void setAllTeamKits(ItemStack itemStack) {
        this.startKits.values().forEach((v) -> v.add(itemStack));
    }
    public void addBombArea(AreaData area){
        this.bombAreaData.add(area);
    }
    public List<AreaData> getBombAreaData() {
        return bombAreaData;
    }
    public void setBlasting(int blasting) {
        isBlasting = blasting;
    }
    public void setExploded(boolean exploded) {
        isExploded = exploded;
    }

    public int isBlasting() {
        return isBlasting;
    }

    public boolean isExploded() {
        return isExploded;
    }

    public void syncToClient() {
        BaseTeam ct = this.getMapTeams().getTeamByName("ct");
        BaseTeam t = this.getMapTeams().getTeamByName("t");
        if(ct == null || t == null) return;
        CSGameSettingsS2CPacket packet = new CSGameSettingsS2CPacket(ct.getScores(),t.getScores(), this.currentPauseTime,this.currentRoundTime,this.isDebug(),this.isStart,this.isError,this.isPause,this.isWaiting,this.isWaitingWinner);
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if(player != null){
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> player), packet);
            }
        }));
    }

    public void resetPlayerClientData(ServerPlayer serverPlayer){
        FPSMatchStatsResetS2CPacket packet = new FPSMatchStatsResetS2CPacket();
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), packet);
    }

    public void resetGunAmmon(){
        this.getMapTeams().getJoinedPlayers().forEach((uuid)->{
            ServerPlayer serverPlayer = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if(serverPlayer != null){
                FPSMUtil.resetAllGunAmmo(serverPlayer);
            }
        });
    }

    @Nullable
    public SpawnPointData getMatchEndTeleportPoint() {
        return matchEndTeleportPoint;
    }

    public void setMatchEndTeleportPoint(SpawnPointData matchEndTeleportPoint) {
        this.matchEndTeleportPoint = matchEndTeleportPoint;
    }

    @SubscribeEvent
    public static void onPlayerKillOnMap(PlayerKillOnMapEvent event){
        if(event.getBaseMap() instanceof CSGameMap csGameMap){
            BaseTeam killerTeam = csGameMap.getMapTeams().getTeamByPlayer(event.getKiller());
            BaseTeam deadTeam = csGameMap.getMapTeams().getTeamByPlayer(event.getDead());
            if(killerTeam == null || deadTeam == null) return;
            if (killerTeam.getFixedName().equals(deadTeam.getFixedName())){
                csGameMap.removePlayerMoney(event.getKiller().getUUID(),300);
                csGameMap.getShop().syncShopMoneyData(event.getKiller().getUUID());
                event.getKiller().displayClientMessage(Component.translatable("fpsm.kill.message.teammate",300),false);
            }else{
                csGameMap.addPlayerMoney(event.getKiller().getUUID(),300);
                csGameMap.getShop().syncShopMoneyData(event.getKiller().getUUID());
                event.getKiller().displayClientMessage(Component.translatable("fpsm.kill.message.enemy",300),false);
            }
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event){
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());
        if(map instanceof CSGameMap csGameMap && event.getRawText().startsWith(".")){
            csGameMap.handleChatCommand(event);
        }
    }

    public void handleChatCommand(ServerChatEvent event){
        COMMANDS.forEach((k,v)->{
            String rawText = event.getRawText();
            if (rawText.contains(k) && rawText.length() - 1 == k.length()){
                v.accept(this,event.getPlayer());
            }
        });
    }

    public int getOvertimeRounds() {
        return overtimeRounds;
    }

    public void setOvertimeRounds(int overtimeRounds) {
        this.overtimeRounds = overtimeRounds;
    }

    public enum WinnerReason{
        TIME_OUT(3250),
        ACED(3250),
        DEFUSE_BOMB(3500),
        DETONATE_BOMB(3500);
        public final int winMoney;

        WinnerReason(int winMoney) {
            this.winMoney = winMoney;
        }
    }
}
