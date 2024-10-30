package com.phasetranscrystal.fpsmatch.core.data;

public class TabData {
    private int kills;
    private int deaths;
    private int assists;
    private float damage;

    public TabData(){
        this.kills = 0;
        this.deaths = 0;
        this.assists = 0;
        this.damage = 0;
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


    public float getDamage() {
        return damage;
    }
}
