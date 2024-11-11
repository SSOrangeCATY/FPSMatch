package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerData extends SavedData {
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

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        // 保存主人的UUID
        tag.putUUID("Owner", this.owner);

        // 保存分数
        tag.putInt("Scores", this.scores);

        // 保存是否离线的状态
        tag.putBoolean("IsOffline", this.isOffline);

        // 保存TabData
        CompoundTag tabDataTag = new CompoundTag();
        this.tabData.save(tabDataTag);
        tag.put("TabData", tabDataTag);

        // 保存临时TabData
        CompoundTag tabDataTempTag = new CompoundTag();
        this.tabDataTemp.save(tabDataTempTag);
        tag.put("TabDataTemp", tabDataTempTag);

        // 保存SpawnPointData
        if (this.spawnPointsData != null) {
            CompoundTag spawnPointsDataTag = new CompoundTag();
            this.spawnPointsData.save(spawnPointsDataTag);
            tag.put("SpawnPointsData", spawnPointsDataTag);
        }

        return tag;
    }

    public static PlayerData load(CompoundTag tag) {
        PlayerData data = PlayerData.create();
        data.owner = tag.getUUID("Owner");
        data.scores = tag.getInt("Scores");

        data.isOffline = tag.getBoolean("IsOffline");

        if (tag.contains("TabData")) {
            CompoundTag tabDataTag = tag.getCompound("TabData");
            data.tabData = TabData.load(tabDataTag);
        }

        if (tag.contains("TabDataTemp")) {
            CompoundTag tabDataTempTag = tag.getCompound("TabDataTemp");
            data.tabData = TabData.load(tabDataTempTag);
        }

        if (tag.contains("SpawnPointsData")) {
            CompoundTag spawnPointsDataTag = tag.getCompound("SpawnPointsData");
            data.spawnPointsData = SpawnPointData.load(spawnPointsDataTag);
        }

        return data;
    }

    public static PlayerData create() {
        return new PlayerData();
    }

}
