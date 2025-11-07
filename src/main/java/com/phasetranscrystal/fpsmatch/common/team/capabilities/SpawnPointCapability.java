package com.phasetranscrystal.fpsmatch.common.team.capabilities;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamCapabilityManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.*;

public class SpawnPointCapability implements TeamCapability {
    public final BaseTeam team;
    private final List<SpawnPointData> spawnPointsData = new ArrayList<>();

    private SpawnPointCapability(BaseTeam team) {
        this.team = team;
    }

    public static void register() {
        TeamCapabilityManager.register(SpawnPointCapability.class, SpawnPointCapability::new);
    }

    public void setAllSpawnPointData(List<SpawnPointData> spawnPointsData) {
        this.spawnPointsData.clear();
        this.spawnPointsData.addAll(spawnPointsData);
    }

    public boolean randomSpawnPoints() {
        Random random = new Random();

        if (this.spawnPointsData.isEmpty()) {
            FPSMCore.getInstance().getServer().sendSystemMessage(Component.translatable("message.fpsmatch.error.no_spawn_points")
                    .append(Component.literal("error from -> " + team.name)).withStyle(ChatFormatting.RED));
            return false;
        }

        Map<UUID, PlayerData> players = this.team.getPlayers();
        if (this.spawnPointsData.size() < players.size()) {
            FPSMCore.getInstance().getServer().sendSystemMessage(Component.translatable("message.fpsmatch.error.not_enough_spawn_points")
                    .append(Component.literal("error from -> " + team.name)).withStyle(ChatFormatting.RED));
            return false;
        }

        List<UUID> playerUUIDs = new ArrayList<>(players.keySet());
        List<SpawnPointData> list = new ArrayList<>(this.spawnPointsData);
        for (UUID playerUUID : playerUUIDs) {
            if (list.isEmpty()) {
                list.addAll(this.spawnPointsData);
            }
            int randomIndex = random.nextInt(list.size());
            SpawnPointData spawnPoint = list.get(randomIndex);
            list.remove(randomIndex);
            players.get(playerUUID).setSpawnPointsData(spawnPoint);
        }
        return true;
    }

    public void addSpawnPointData(@Nonnull SpawnPointData data) {
        this.spawnPointsData.add(data);
    }

    public void addAllSpawnPointData(@Nonnull List<SpawnPointData> data) {
        this.spawnPointsData.addAll(data);
    }

    public List<SpawnPointData> getSpawnPointsData() {
        return spawnPointsData;
    }

    public void clearSpawnPointsData() {
        spawnPointsData.clear();
    }

    @Override
    public void reset() {
        randomSpawnPoints();
    }

    @Override
    public void destroy() {
        clearSpawnPointsData();
    }
}