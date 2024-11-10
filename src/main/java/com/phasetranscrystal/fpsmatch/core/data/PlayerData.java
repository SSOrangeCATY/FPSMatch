package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerData extends SavedData {
    private final UUID owner;
    private int scores = 0;
    private final TabData tabData;
    private final TabData tabDataTemp;
    private boolean isOffline = false;
    private SpawnPointData spawnPointsData;

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

    @Override
    public @NotNull CompoundTag save(CompoundTag pCompoundTag) {
        // 保存主人的UUID
        pCompoundTag.putUUID("Owner", this.owner);

        // 保存分数
        pCompoundTag.putInt("Scores", this.scores);

        // 保存是否离线的状态
        pCompoundTag.putBoolean("IsOffline", this.isOffline);

        // 保存TabData
        CompoundTag tabDataTag = new CompoundTag();
        this.tabData.save(tabDataTag);
        pCompoundTag.put("TabData", tabDataTag);

        // 保存临时TabData
        CompoundTag tabDataTempTag = new CompoundTag();
        this.tabDataTemp.save(tabDataTempTag);
        pCompoundTag.put("TabDataTemp", tabDataTempTag);

        // 保存SpawnPointData
        if (this.spawnPointsData != null) {
            CompoundTag spawnPointsDataTag = new CompoundTag();
            this.spawnPointsData.save(spawnPointsDataTag);
            pCompoundTag.put("SpawnPointsData", spawnPointsDataTag);
        }

        return pCompoundTag;
    }
}
