package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class PlayerData{
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
    private boolean vote = false;

    public PlayerData(Player owner) {
        this.owner = owner.getUUID();
        this.name = owner.getDisplayName();
    }

    public Component name(){
        return this.name;
    }

    public void setSpawnPointsData(SpawnPointData spawnPointsData) {
        this.spawnPointsData = spawnPointsData;
    }

    public void addScore(int scores){
        this.scores += scores;
    }

    public void setScores(int scores){
        this.scores = scores;
    }

    public int getScores() {
        return scores;
    }

    public SpawnPointData getSpawnPointsData() {
        return spawnPointsData;
    }

    public boolean isOnline() {
        return FPSMCore.getInstance().getPlayerByUUID(this.owner).isPresent();
    }

    public Optional<ServerPlayer> getPlayer() {
        return FPSMCore.getInstance().getPlayerByUUID(this.owner);
    }

    public boolean isVote() {
        return vote;
    }

    public void setVote(boolean vote) {
        this.vote = vote;
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
    }

    public boolean isLiving() {
        return isLiving && this.isOnline();
    }

    public void setMvpCount(int mvpCount) {
        this.mvpCount = mvpCount;
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

    public int _assists() {
        return _assists;
    }

    public int _deaths() {
        return _deaths;
    }

    public int _kills() {
        return _kills;
    }

    public int getKills() {
        return kills;
    }

    public void addDeaths(){
        this._deaths += 1;
    }

    public void addAssist(){
        this._assists += 1;
    }

    public void addKills(){
        this._kills += 1;
    }

    public void addDamage(float damage){
        this._damage += damage;
    }

    public float getDamage() {
        return damage;
    }

    public void setKills(int i) {
        this.kills = i;
    }

    public void setAssists(int assists) {
        this.assists = assists;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
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
    }

    public void setHeadshotKills(int headshotKills) {
        this.headshotKills = headshotKills;
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
}
