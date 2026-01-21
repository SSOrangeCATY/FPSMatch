package com.phasetranscrystal.fpsmatch.core.data;

import com.google.gson.Gson;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * 玩家数据类
 * 核心规则：
 * 1. enableRounds=true：使用回合临时字段（_开头），最终合并到基础字段
 * 2. enableRounds=false：直接操作基础字段，无回合临时数据
 * 3. 客户端仅能访问聚合后的非_开头字段，不直接接触回合临时字段
 * 4. 同步逻辑通过SyncFieldType枚举管控，仅同步需要的字段
 */
public class PlayerData {
    private static final Gson GSON = new Gson();
    private final UUID owner;
    private final Component name;
    //基础字段（客户端/服务端共用，客户端仅访问）
    private int scores = 0;
    private int kills = 0;
    private int deaths = 0;
    private int assists = 0;
    private float damage = 0.0f;
    private int mvpCount = 0;
    private boolean isLiving = true;
    private int headshotKills = 0;

    //回合临时字段（仅服务端使用，enableRounds=true时生效）
    private int _kills = 0; // 本回合击杀
    private int _deaths = 0; // 本回合死亡
    private int _assists = 0; // 本回合助攻
    private float _damage = 0.0f; // 本回合伤害

    //服务端独有字段
    private final Map<UUID, Damage> damageData = new HashMap<>(); // 伤害明细
    private SpawnPointData spawnPointsData;
    private boolean dirty = true; // 脏数据标记
    public final boolean enableRounds; // 是否启用回合模式

    //客户端独有字段
    @OnlyIn(Dist.CLIENT)
    private float hp; // 生命值百分比

    public PlayerData(Player owner) {
        this(owner.getUUID(), owner.getDisplayName(), true);
    }

    public PlayerData(UUID owner, Component name) {
        this(owner, name, true);
    }

    public PlayerData(UUID owner, Component name, boolean enableRounds) {
        this.owner = owner;
        this.name = name;
        this.enableRounds = enableRounds;
    }

    public PlayerData(Player owner, boolean enableRounds) {
        this(owner.getUUID(), owner.getDisplayName(), enableRounds);
    }

    //  基础状态
    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    //  客户端可访问的字段 
    public Component name() {
        return this.name;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getScores() {
        return scores;
    }

    public float getHpServer(){
        return getPlayer().map(p->this.isLivingServer() ? p.getHealth() : 0.0f).orElse(0.0f);
    }

    // 击杀数
    public int getKills() {
        return kills;
    }

    public int getTempKills(){
        return _kills;
    }

    // 死亡数
    public int getDeaths() {
        return deaths;
    }

    public int getTempDeaths(){
        return _deaths;
    }

    // 助攻数
    public int getAssists() {
        return assists;
    }

    public int getTempAssists(){
        return _assists;
    }

    // 总伤害
    public float getDamage() {
        return damage;
    }

    public float getTempDamage(){
        return _damage;
    }

    public int getMvpCount() {
        return mvpCount;
    }

    // 爆头率
    public float getHeadshotRate() {
        int Kills = getKills();
        return Kills > 0 ? (float) headshotKills / Kills : 0.0f;
    }

    //KD
    public float getKD() {
        int Deaths = getDeaths();
        return Deaths > 0 ? (float) getKills() / Deaths : 0.0f;
    }

    // 客户端用的存活状态
    @OnlyIn(Dist.CLIENT)
    public boolean isLiving() {
        return isLiving;
    }

    // 服务端用的存活状态（含在线检查）
    public boolean isLivingServer() {
        return isLiving && isOnline();
    }

    public int getHeadshotKills() {
        return headshotKills;
    }

    // 客户端生命值百分比（仅客户端访问）
    @OnlyIn(Dist.CLIENT)
    public float getHealthPercent() {
        return hp;
    }

    //  服务端字段操作 
    public void addKill() {
        if (enableRounds) {
            _kills++;
        } else {
            kills++;
        }
        markDirty();
    }

    public void setKills(int kills) {
        if (enableRounds) {
            this._kills = kills;
        } else {
            this.kills = kills;
        }
        markDirty();
    }

    // 死亡数操作
    public void addDeath() {
        if (enableRounds) {
            _deaths++;
        } else {
            deaths++;
        }
        markDirty();
    }

    public void setDeaths(int deaths) {
        if (enableRounds) {
            this._deaths = deaths;
        } else {
            this.deaths = deaths;
        }
        markDirty();
    }

    // 助攻数操作
    public void addAssist() {
        if (enableRounds) {
            _assists++;
        } else {
            assists++;
        }
        markDirty();
    }

    public void setAssists(int assists) {
        if (enableRounds) {
            this._assists = assists;
        } else {
            this.assists = assists;
        }
        markDirty();
    }

    // 伤害操作
    public void addDamage(float value) {
        if (enableRounds) {
            _damage += value;
        } else {
            damage += value;
        }
        markDirty();
    }

    public void setDamage(float damage) {
        if (enableRounds) {
            this._damage = damage;
        } else {
            this.damage = damage;
        }
        markDirty();
    }
    

    public void setTempKills(int tempKills) {
        this._kills = tempKills;
        markDirty();
    }

    public void setTempDeaths(int tempDeaths) {
        this._deaths = tempDeaths;
        markDirty();
    }

    public void setTempAssists(int tempAssists) {
        this._assists = tempAssists;
        markDirty();
    }

    public void setTempDamage(float tempDamage) {
        this._damage = damage;
        markDirty();
    }

    public void addScore(int score) {
        this.scores += score;
        markDirty();
    }

    public void setScores(int scores) {
        this.scores = scores;
        markDirty();
    }

    public void setMvpCount(int mvpCount) {
        this.mvpCount = mvpCount;
        markDirty();
    }

    public void addMvpCount(int count) {
        this.mvpCount += count;
        addScore(4);
    }

    public void setLiving(boolean living) {
        this.isLiving = living;
        markDirty();
    }

    public void addHeadshotKill() {
        this.headshotKills++;
        markDirty();
    }

    public void setHeadshotKills(int headshotKills) {
        this.headshotKills = headshotKills;
        markDirty();
    }

    //  服务端独有方法（不同步） 
    public void setSpawnPointsData(SpawnPointData spawnPointsData) {
        this.spawnPointsData = spawnPointsData;
    }

    public SpawnPointData getSpawnPointsData() {
        return spawnPointsData;
    }

    public Map<UUID, Damage> getDamageData() {
        return damageData;
    }

    public Map<UUID, Float> getDamages(){
        Map<UUID, Float> damages = new HashMap<>();
        for (Map.Entry<UUID, Damage> entry : damageData.entrySet()) {
            damages.put(entry.getKey(),entry.getValue().damage);
        }
        return damages;
    }

    public void addDamageData(UUID hurtPlayer, float damage) {
        this.getDamageData().computeIfAbsent(hurtPlayer,k->new Damage()).addDamage(damage);
        addDamage(damage);
    }

    public void clearDamageData() {
        damageData.clear();
    }

    public boolean isOnline() {
        if (!FPSMCore.initialized()) {
            throw new RuntimeException("isOnline() only available on server side");
        }
        return FPSMCore.getInstance().getPlayerByUUID(owner).isPresent();
    }

    public Optional<ServerPlayer> getPlayer() {
        if (!FPSMCore.initialized()) {
            throw new RuntimeException("getPlayer() only available on server side");
        }
        return FPSMCore.getInstance().getPlayerByUUID(owner);
    }

    @OnlyIn(Dist.CLIENT)
    public void setHealthPercent(float hp) {
        this.hp = hp;
    }

    // 回合结束：合并临时数据到基础字段，清空临时数据
    public void saveRoundData() {
        if (!enableRounds) return;

        this.kills += _kills;
        this.deaths += _deaths;
        this.assists += _assists;
        this.damage += _damage;
        this.scores += (_kills * 2) + _assists; // 回合得分结算

        this._kills = 0;
        this._deaths = 0;
        this._assists = 0;
        this._damage = 0;
        this.damageData.clear();

        markDirty();
    }

    // 重置所有数据
    public void reset() {
        this.scores = 0;
        this.kills = 0;
        this.deaths = 0;
        this.assists = 0;
        this.damage = 0.0f;
        this.mvpCount = 0;
        this.isLiving = true;
        this.headshotKills = 0;

        this._kills = 0;
        this._deaths = 0;
        this._assists = 0;
        this._damage = 0.0f;

        this.damageData.clear();
        markDirty();
    }

    public void resetWithSpawnPoint() {
        reset();
        this.spawnPointsData = null;
    }

    public PlayerData copy(Player targetPlayer) {
        return copy(targetPlayer, this.enableRounds);
    }

    public PlayerData copy(Player targetPlayer, boolean enableRounds) {
        PlayerData copy = new PlayerData(targetPlayer, enableRounds);
        copy.setScores(this.scores);
        copy.setKills(this.getKills());
        copy.setDeaths(this.getDeaths());
        copy.setAssists(this.getAssists());
        copy.setDamage(this.getDamage());
        copy.setMvpCount(this.mvpCount);
        copy.setLiving(this.isLiving);
        copy.setHeadshotKills(this.headshotKills);
        return copy;
    }

    public void merge(PlayerData other) {
        this.setKills(this.getKills() + other.getKills());
        this.setDeaths(this.getDeaths() + other.getDeaths());
        this.setAssists(this.getAssists() + other.getAssists());
        this.setDamage(this.getDamage() + other.getDamage());
        this.setHeadshotKills(this.headshotKills + other.headshotKills);
    }

    // 客户端Tab栏展示用
    @OnlyIn(Dist.CLIENT)
    public String getTabString() {
        return getKills() + "/" + getDeaths() + "/" + getAssists();
    }

    public String info() {
        return GSON.toJson(mappedInfo());
    }

    public Map<String, Object> mappedInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("owner", owner.toString());
        info.put("name", name.getString());
        info.put("enableRounds", enableRounds);
        info.put("living", isLiving);
        info.put("scores", scores);
        info.put("Kills", getKills());
        info.put("Deaths", getDeaths());
        info.put("Assists", getAssists());
        info.put("Damage", getDamage());
        info.put("headshotKills", headshotKills);
        info.put("mvpCount", mvpCount);
        info.put("hp", FPSMCore.initialized() ? healthPercentServer() : hp);
        return info;
    }

    // 服务端计算生命值百分比
    public float healthPercentServer() {
        Optional<ServerPlayer> player = getPlayer();
        if (player.isEmpty()) return 0.0f;

        float maxHealth = player.get().getMaxHealth();
        float currentHealth = player.get().getHealth();
        return maxHealth <= 0 ? 0.0f : currentHealth / maxHealth;
    }

    public static class Damage{
        public int count = 0;
        public float damage = 0;

        public void merge(Damage other) {
            this.count += other.count;
            this.damage += other.damage;
        }

        public void addDamage(float damage) {
            this.count++;
            this.damage += damage;
        }
    }
}