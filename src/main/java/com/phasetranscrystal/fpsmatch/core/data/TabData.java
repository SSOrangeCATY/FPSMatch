package com.phasetranscrystal.fpsmatch.core.data;

public class TabData {
    private int kills;
    private int deaths;
    private int assist;

    public TabData(){
        this.kills = 0;
        this.deaths = 0;
        this.assist = 0;
    }

    public int getAssist() {
        return assist;
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
        this.assist += 1;
        return this;
    }
    public TabData addKills(){
        this.kills += 1;
        return this;
    }
}
