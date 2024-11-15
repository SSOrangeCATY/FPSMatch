package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
    private int money;
    private int mvpCount;
    private boolean isLiving;

    public TabData(UUID owner){
        this.owner = owner;
        this.kills = 0;
        this.deaths = 0;
        this.assists = 0;
        this.damage = 0;
        this.money = 0;
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

    public void setMoney(int money) {
        this.money = money;
    }

    public void addMoney(int money){
        this.money += money;
    }

    public int getMoney(){
        return this.money;
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
        tabData.setAssists(assists);
        tabData.setDeaths(deaths);
        tabData.setKills(kills);
        return tabData;
    }

    public TabData getTempData(){
        return new TabData(this.owner);
    }

    public void merge(TabData data){
        this.setKills(this.kills + data.kills);
        this.setDeaths(this.deaths + data.deaths);
        this.setAssists(this.assists + data.assists);
        this.setDamage(this.damage + data.damage);
    }

    public String getTabString(){
        return kills + "/" + deaths + "/" + assists;
    }

}
