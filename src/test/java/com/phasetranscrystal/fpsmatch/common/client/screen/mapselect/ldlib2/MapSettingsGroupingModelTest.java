package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapSettingsGroupingModelTest {
    @Test
    void groupsMatchingCategoriesWithoutChangingFirstSeenOrder() {
        List<MapSettingsGroupingModel.Group> groups = MapSettingsGroupingModel.group(List.of(
                setting("startEconomy", "eco"),
                setting("allowFriendlyFire", "player"),
                setting("aceEconomy", "eco"),
                setting("displayName", "default")
        ));

        assertEquals(List.of("eco", "player", "default"),
                groups.stream().map(MapSettingsGroupingModel.Group::category).toList());
        assertEquals(List.of("startEconomy", "aceEconomy"),
                groups.get(0).settings().stream().map(MapRoomSettingInfo::name).toList());
    }

    @Test
    void emptySelectionShowsAllCategoriesAndMultipleSelectionsFilterTogether() {
        List<MapRoomSettingInfo> settings = List.of(
                setting("startEconomy", "eco"),
                setting("allowFriendlyFire", "player"),
                setting("roundEconomy", "eco"),
                setting("roundTime", "match")
        );

        assertEquals(List.of("eco", "player", "match"),
                MapSettingsGroupingModel.group(settings, Set.of()).stream()
                        .map(MapSettingsGroupingModel.Group::category).toList());
        assertEquals(List.of("eco", "match"),
                MapSettingsGroupingModel.group(settings, Set.of("eco", "match")).stream()
                        .map(MapSettingsGroupingModel.Group::category).toList());
        assertEquals(List.of("eco", "player", "match"),
                MapSettingsGroupingModel.categories(settings));
    }

    private static MapRoomSettingInfo setting(String name, String category) {
        return new MapRoomSettingInfo(name, "1", "1", true,
                "setting.cs." + name, MapRoomSettingInfo.SettingType.INTEGER,
                "setting.cs." + name + ".desc", false, 0.0, 0.0, 1.0, category);
    }
}
