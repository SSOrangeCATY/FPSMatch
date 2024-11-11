package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerData extends SavedData {
    private final UUID owner;
    private int scores = 0;
    private final TabData tabData;
    private final TabData tabDataTemp;
    private boolean isOffline = false;
    private SpawnPointData spawnPointsData;

    public PlayerData(UUID owner,TabData data,TabData dataTemp) {
        this.owner = owner;
        this.tabData = data;
        this.tabDataTemp = dataTemp;
    }

    public PlayerData(UUID owner,int scores,TabData data,TabData dataTemp,boolean isOffline) {
        this.owner = owner;
        this.scores = scores;
        this.tabData = data;
        this.tabDataTemp = dataTemp;
        this.isOffline = isOffline;
    }

    public PlayerData(UUID owner,int scores,TabData data,TabData dataTemp,boolean isOffline,SpawnPointData spawnPointsData) {
        this.owner = owner;
        this.scores = scores;
        this.tabData = data;
        this.tabDataTemp = dataTemp;
        this.isOffline = isOffline;
        this.spawnPointsData = spawnPointsData;
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
    public @NotNull CompoundTag save(@NotNull CompoundTag pCompoundTag) {
        CompoundTag tag = new CompoundTag();
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
        pCompoundTag.put(this.getOwner().toString(),tag);
        return pCompoundTag;
    }

    public PlayerData load(CompoundTag tag) {
        // 读取分数
        this.scores = tag.getInt("Scores");

        // 读取是否离线的状态
        this.isOffline = tag.getBoolean("IsOffline");

        // 读取TabData
        if (tag.contains("TabData")) {
            CompoundTag tabDataTag = tag.getCompound("TabData");
            this.tabData.load(tabDataTag);
        }

        // 读取临时TabData
        if (tag.contains("TabDataTemp")) {
            CompoundTag tabDataTempTag = tag.getCompound("TabDataTemp");
            this.tabDataTemp.load(tabDataTempTag);
        }

        // 读取SpawnPointData
        if (tag.contains("SpawnPointsData")) {
            CompoundTag spawnPointsDataTag = tag.getCompound("SpawnPointsData");
            this.spawnPointsData = SpawnPointData.load(spawnPointsDataTag);
        }

        return this;
    }

    public PlayerData create() {
        return this;
    }

    public PlayerData getData(ServerPlayer player) {
        ServerLevel level = player.serverLevel().getLevel();
        return level.getDataStorage().get(this::load, "playerData_"+ player.getUUID());
    }

    public void syncFileData(DimensionDataStorage storage){
        storage.computeIfAbsent(this::load,this::create,"playerData_"+ owner.toString());
    }
}
