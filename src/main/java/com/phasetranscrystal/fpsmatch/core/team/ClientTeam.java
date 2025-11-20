package com.phasetranscrystal.fpsmatch.core.team;

import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.entity.FPSMPlayer;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class ClientTeam extends BaseTeam {
    public final Map<UUID, PlayerData> players;

    public ClientTeam(String gameType, String mapName, TeamData data) {
        super(gameType, mapName, data.name(), -1, null);
        players = new HashMap<>();
        for (String cap : data.capabilities()){
            FPSMCapabilityManager.getRegisteredCapabilityClassByFormated(cap, TeamCapability.class).ifPresent(this.getCapabilityMap()::add);
        }
    }

    @Override
    public void join(FPSMPlayer player) {
        this.players.put(player.uuid(),new PlayerData(player.uuid(),player.get().getDisplayName()));
    }

    public void join(UUID uuid, PlayerData data) {
        this.players.put(uuid,data);
    }

    public void join(UUID uuid, Component displayName) {
        this.players.put(uuid,new PlayerData(uuid,displayName));
    }

    @Override
    public void leave(FPSMPlayer player) {
        this.delPlayer(player.uuid());
    }

    @Override
    public void delPlayer(UUID player) {
        this.players.remove(player);
    }

    @Override
    public void resetLiving() {

    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID player) {
        return players.containsKey(player) ? Optional.of(players.get(player)) : Optional.empty();
    }

    @Override
    public List<PlayerData> getPlayersData() {
        return new ArrayList<>(players.values());
    }

    @Override
    public List<UUID> getPlayerList() {
        return new ArrayList<>(players.keySet());
    }

    @Override
    public boolean hasPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    @Override
    public int getPlayerCount() {
        return players.size();
    }

    @Override
    public boolean isEmpty() {
        return players.isEmpty();
    }

    @Override
    public Map<UUID, PlayerData> getPlayers() {
        return players;
    }

    public void setPlayerData(UUID player, PlayerData data) {
        players.put(player,data);
    }

    @Override
    public void clearAndPutPlayers(Map<UUID, PlayerData> players) {
        this.players.clear();
        this.players.putAll(players);
    }

    @Override
    public void sendMessage(Component message, boolean onlyLiving) {

    }

    @Override
    public boolean isClientSide() {
        return true;
    }

}