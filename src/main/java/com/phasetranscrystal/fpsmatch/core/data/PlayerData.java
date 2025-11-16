package com.phasetranscrystal.fpsmatch.core.data;

import com.google.gson.Gson;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.entity.FPSMPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;
/**
 * 带_前缀的是回合临时数据
 * */
public class PlayerData{
    private static final Gson GSON = new Gson();
    private final UUID owner;
    private final Component name;
    private int scores = 0;
    private final Map<UUID,Float> damageData = new HashMap<>();
    private int kills;
    private int _kills = 0;
    private int deaths;
    private int _deaths = 0;
    private int assists;
    private int _assists = 0;
    private float damage;
    private float _damage = 0;
    private int mvpCount;
    private boolean isLiving;
    private int headshotKills;
    private SpawnPointData spawnPointsData;

    private boolean dirty;

    // Client
    private float hp;

    public PlayerData(Player owner) {
        this.owner = owner.getUUID();
        this.name = owner.getDisplayName();
    }

    public PlayerData(UUID owner, Component name) {
        this.owner = owner;
        this.name = name;
    }

    public boolean isDirty(){
        return dirty;
    }

    public void markDirty(){
        dirty = true;
    }

    public void setDirty(boolean b){
        dirty = b;
    }

    public Component name(){
        return this.name;
    }

    public void setSpawnPointsData(SpawnPointData spawnPointsData) {
        this.spawnPointsData = spawnPointsData;
    }

    public void addScore(int scores){
        this.scores += scores;
        markDirty();
    }

    public void setScores(int scores){
        this.scores = scores;
        markDirty();
    }

    public int getScores() {
        return scores;
    }

    public SpawnPointData getSpawnPointsData() {
        return spawnPointsData;
    }

    public boolean isOnline() {
        if(!FPSMCore.initialized()) throw new RuntimeException("isOnline method onlyIn serverSide");
        return FPSMCore.getInstance().getPlayerByUUID(this.owner).isPresent();
    }

    public Optional<ServerPlayer> getPlayer() {
        if(!FPSMCore.initialized()) throw new RuntimeException("isOnline method onlyIn serverSide");
        return FPSMCore.getInstance().getPlayerByUUID(this.owner);
    }

    public UUID getOwner() {
        return owner;
    }

    public Map<UUID, Float> getDamageData() {
        return damageData;
    }

    public void setDamageData(UUID hurt, float value){
        this.damageData.put(hurt,value);
    }

    public void addDamageData(UUID hurt, float value){
        this.damageData.merge(hurt,value,Float::sum);
        this.addDamage(value);
    }

    public void clearDamageData(){
        damageData.clear();
    }

    public void setLiving(boolean living) {
        isLiving = living;
        markDirty();
    }

    public boolean isLiving() {
        return isLiving && this.isOnline();
    }

    public boolean isLivingNoOnlineCheck() {
        return isLiving;
    }

    public void setMvpCount(int mvpCount) {
        this.mvpCount = mvpCount;
        markDirty();
    }

    public int getMvpCount() {
        return mvpCount;
    }

    public int getAssists() {
        return assists;
    }

    public int getDeaths() {
        return deaths;
    }

    public float _damage() {
        return _damage;
    }

    public void set_damage(float _damage) {
        this._damage = _damage;
        markDirty();
    }

    public int _assists() {
        return _assists;
    }

    public void set_assists(int _assists) {
        this._assists = _assists;
        markDirty();
    }

    public int _deaths() {
        return _deaths;
    }

    public void set_deaths(int _deaths) {
        this._deaths = _deaths;
        markDirty();
    }

    public int _kills() {
        return _kills;
    }

    public void set_kills(int _kills) {
        this._kills = _kills;
        markDirty();
    }

    public int getKills() {
        return kills;
    }

    public void addDeaths(){
        this._deaths += 1;
        markDirty();
    }

    public void addAssist(){
        this._assists += 1;
        markDirty();
    }

    public void addKills(){
        this._kills += 1;
        markDirty();
    }

    public float h(){
        if(!FPSMCore.initialized()) throw new RuntimeException("isOnline method onlyIn serverSide");
        return this.isLiving ? this.getPlayer().map(Player::getHealth).orElse(0f) : 0f;
    }

    public float hp(){
        if(!FPSMCore.initialized()) throw new RuntimeException("isOnline method onlyIn serverSide");
        float maxHealth = this.getPlayer().map(Player::getMaxHealth).orElse(0f);
        float health = this.h();
        if(maxHealth == 0 || health == 0) return 0;
        return health / maxHealth;
    }

    public float healthPercent(){
        return FPSMCore.initialized() ? this.hp() : this.hp;
    }

    public void setHp(float hp) {
        this.hp = hp;

    }

    public void addDamage(float damage){
        this._damage += damage;
        markDirty();
    }

    public float getDamage() {
        return damage;
    }

    public void setKills(int i) {
        this.kills = i;
        markDirty();
    }

    public void setAssists(int assists) {
        this.assists = assists;
        markDirty();
    }

    public void setDamage(float damage) {
        this.damage = damage;
        markDirty();
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
        markDirty();
    }

    public void addMvpCount(int mvpCount){
        this.mvpCount += mvpCount;
        addScore(4);
    }

    public PlayerData copy(Player other){
        PlayerData data = new PlayerData(other);
        data.setDamage(damage);
        data.setMvpCount(mvpCount);
        data.setAssists(assists);
        data.setDeaths(deaths);
        data.setKills(kills);
        data.setHeadshotKills(headshotKills);
        return data;
    }

    public void merge(PlayerData data){
        this.setKills(this.kills + data.kills);
        this.setDeaths(this.deaths + data.deaths);
        this.setAssists(this.assists + data.assists);
        this.setDamage(this.damage + data.damage);
        this.setHeadshotKills(this.headshotKills + data.headshotKills);
    }

    public String getTabString(){
        return kills + "/" + deaths + "/" + assists;
    }

    public int getHeadshotKills() {
        return headshotKills;
    }

    public void addHeadshotKill() {
        this.headshotKills++;
        markDirty();
    }

    public void setHeadshotKills(int headshotKills) {
        this.headshotKills = headshotKills;
        markDirty();
    }

    public void save() {
        this.deaths += this._deaths;
        this._deaths = 0;
        this.assists += this._assists;
        addScore(this._assists);
        this._assists = 0;
        this.damage += this._damage;
        this._damage = 0;
        this.kills += this._kills;
        addScore(this._kills * 2);
        this._kills = 0;
        this.damageData.clear();
    }

    public void reset(){
        this._assists = 0;
        this.assists = 0;
        this._deaths = 0;
        this.deaths = 0;
        this._damage = 0;
        this.damage = 0;
        this._kills = 0;
        this.kills = 0;
        this.mvpCount = 0;
        this.isLiving = true;
        this.damageData.clear();
        this.headshotKills = 0;
        this.scores = 0;
        markDirty();
    }

    public void resetWithSpawnPoint(){
        this.reset();
        this.spawnPointsData = null;
    }

    public String info(){
        return GSON.toJson(mappedInfo());
    }

    public Map<String,Object> mappedInfo(){
        Map<String,Object> info = new HashMap<>();
        info.put("owner",this.owner.toString());
        info.put("name",this.name.getString());
        info.put("living",this.isLiving);
        info.put("scores",this.scores);
        info.put("kills",this.kills + _kills);
        info.put("deaths",this.deaths + this._deaths);
        info.put("assists",this.assists + this._assists);
        info.put("damage",this.damage + this._damage);
        info.put("headshotKills",this.headshotKills);
        info.put("mvpCount",this.mvpCount);
        info.put("hp",healthPercent());
        return info;
    }
}
