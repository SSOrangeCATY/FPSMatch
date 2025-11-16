package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamSwitchRestrictionCapability extends TeamCapability {
    private final BaseTeam team;

    private final List<UUID> unableToSwitchPlayers = new ArrayList<>();

    private TeamSwitchRestrictionCapability(BaseTeam team) {
        this.team = team;
    }

    public static void register() {
        FPSMCapabilityManager.register(TeamSwitchRestrictionCapability.class, TeamSwitchRestrictionCapability::new);
    }

    public void addUnableToSwitchPlayer(UUID playerUUID) {
        if (!unableToSwitchPlayers.contains(playerUUID)) {
            unableToSwitchPlayers.add(playerUUID);
        }
    }

    public void removeUnableToSwitchPlayer(UUID playerUUID) {
        unableToSwitchPlayers.remove(playerUUID);
    }

    public boolean isUnableToSwitch(UUID playerUUID) {
        return unableToSwitchPlayers.contains(playerUUID);
    }

    public void clearUnableToSwitchPlayers() {
        unableToSwitchPlayers.clear();
    }

    public List<UUID> getUnableToSwitchPlayers() {
        return new ArrayList<>(unableToSwitchPlayers);
    }

    @Override
    public void destroy() {
        clearUnableToSwitchPlayers();
    }

    @Override
    public BaseTeam getHolder(){
        return team;
    }
}