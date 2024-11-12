package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerData{
    private UUID owner;
    private int scores = 0;
    private TabData tabData;
    private TabData tabDataTemp;
    private boolean isOffline = false;
    private SpawnPointData spawnPointsData;

    public PlayerData() {
    }

    public PlayerData(UUID owner) {
        this.owner = owner;
        this.tabData = new TabData(owner);
        this.tabDataTemp = new TabData(owner);
    }

    public void setLiving(boolean b) {
        this.tabData.setLiving(b);
    }

    public void setOffline(boolean b) {
        this.isOffline = b;
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

    public UUID getOwner() {
        return owner;
    }

    public int getScores() {
        return scores;
    }

    public TabData getTabData() {
        return tabData;
    }

    public TabData getTabDataTemp() {
        return tabDataTemp;
    }

    public SpawnPointData getSpawnPointsData() {
        return spawnPointsData;
    }

    public boolean isOffline() {
        return isOffline;
    }
}
