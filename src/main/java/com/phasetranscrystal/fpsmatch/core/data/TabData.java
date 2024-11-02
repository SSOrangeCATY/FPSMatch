package com.phasetranscrystal.fpsmatch.core.data;

public class TabData {
    private int kills;
    private int deaths;
    private int assists;
    private float damage;
    private int money;

    public TabData(){
        this.kills = 0;
        this.deaths = 0;
        this.assists = 0;
        this.damage = 0;
        this.money = 0;
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

    public TabData addDeaths(){
        this.deaths += 1;
        return this;
    }

    public TabData addAssist(){
        this.assists += 1;
        return this;
    }
    public TabData addKills(){
        this.kills += 1;
        return this;
    }

    public TabData addDamage(float damage){
        this.damage += damage;
        return this;
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
}
