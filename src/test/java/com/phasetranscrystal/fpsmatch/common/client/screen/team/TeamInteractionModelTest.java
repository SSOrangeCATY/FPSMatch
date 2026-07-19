package com.phasetranscrystal.fpsmatch.common.client.screen.team;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomTeamInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamInteractionModelTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void dragOnlyProducesDropAfterMovementOverAnotherAvailableTeam() {
        TeamDragState state = new TeamDragState();
        state.begin(PLAYER, "red", 10, 10);

        assertTrue(state.update(10, 10, "blue").isEmpty());
        assertTrue(state.update(16, 10, "blue").isEmpty());
        assertEquals(java.util.Optional.of(new TeamDragState.Drop(PLAYER, "red", "blue")), state.release());
        assertFalse(state.active());
    }

    @Test
    void cancellingDragNeverEmitsAnAction() {
        TeamDragState state = new TeamDragState();
        state.begin(PLAYER, "red", 10, 10);
        state.update(20, 10, "blue");
        state.cancel();

        assertTrue(state.release().isEmpty());
        assertFalse(state.active());
    }

    @Test
    void teamActionsExcludeSpectatorFullAndSameTeams() {
        MapRoomDetail detail = detail(false);

        assertEquals(List.of("blue"), TeamActionModel.availableTargetTeams(detail, PLAYER));
        assertTrue(TeamActionModel.canKick(detail, PLAYER));
        assertFalse(TeamActionModel.canKick(detail, UUID.fromString("00000000-0000-0000-0000-000000000099")));
    }

    @Test
    void targetTeamsAreDisabledWhenRoomCannotAcceptSwitches() {
        MapRoomDetail detail = detail(true);

        assertTrue(TeamActionModel.availableTargetTeams(detail, PLAYER).isEmpty());
        assertFalse(TeamActionModel.canDropTo(detail, PLAYER, "blue"));
    }

    @Test
    void nonOperatorCanSwitchSelfButCannotKick() {
        MapRoomDetail detail = detail(false, false);

        assertEquals(List.of("blue"), TeamActionModel.availableTargetTeams(detail, PLAYER));
        assertFalse(TeamActionModel.canKick(detail, PLAYER));
    }

    private static MapRoomDetail detail(boolean started) {
        return detail(started, true);
    }

    private static MapRoomDetail detail(boolean started, boolean operator) {
        MapRoomSummary summary = new MapRoomSummary(
                "cs", "de_test", "Test", "", "0-0", started, false,
                !started, 1, 2, true, false, operator, 0);
        MapRoomPlayerInfo player = new MapRoomPlayerInfo(PLAYER, "Player", "red", false, true, false);
        return new MapRoomDetail(
                summary,
                List.of(player),
                List.<MapRoomSettingInfo>of(),
                List.of(),
                List.of(),
                List.of(
                        new MapRoomTeamInfo("red", 1, 2, false),
                        new MapRoomTeamInfo("blue", 0, 2, false),
                        new MapRoomTeamInfo("spectator", 0, -1, true)
                ),
                Set.of(),
                "", "", ""
        );
    }
}
