package com.phasetranscrystal.fpsmatch.core.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TabData {
    private final UUID owner;
    private final Map<UUID,Float> damageData = new HashMap<>();
    private int kills;
    private int deaths;
    private int assists;
    private float damage;
    private int mvpCount;
    private boolean isLiving;
    private int headshotKills;

    public TabData(UUID owner){
        this.owner = owner;
        this.kills = 0;
        this.deaths = 0;
        this.assists = 0;
        this.damage = 0;
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
        return isLiving;
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

    public int getKills() {
        return kills;
    }

    public void addDeaths(){
        this.deaths += 1;
    }

    public void addAssist(){
        this.assists += 1;
    }
    public void addKills(){
        this.kills += 1;
    }

    public void addDamage(float damage){
        this.damage += damage;
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
    }

    public TabData copy(){
        TabData tabData = new TabData(owner);
        tabData.setDamage(damage);
        tabData.setMvpCount(mvpCount);
        tabData.setAssists(assists);
        tabData.setDeaths(deaths);
        tabData.setKills(kills);
        tabData.setHeadshotKills(headshotKills);
        return tabData;
    }

    public void merge(TabData data){
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

}
