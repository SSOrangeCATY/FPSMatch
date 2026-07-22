package com.phasetranscrystal.fpsmatch.common.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.phasetranscrystal.fpsmatch.core.data.Setting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRoomSettingPresentationTest {
    @Test
    void smallDecimalUsesBoundedSliderMetadata() {
        MapRoomSettingInfo info = MapRoomQueryService.settingInfo(
                Setting.of("player", "minAssistDamageRatio", 0.25f), true, "cs");

        assertEquals(MapRoomSettingInfo.SettingType.DECIMAL, info.type());
        assertTrue(info.slider());
        assertEquals(0.0, info.minValue());
        assertEquals(1.0, info.maxValue());
        assertEquals(0.01, info.step());
        assertEquals("setting.base.minAssistDamageRatio.desc", info.descriptionKey());
        assertEquals("player", info.category());
        assertEquals("setting.fpsm.category.player", info.categoryTranslationKey());
    }

    @Test
    void smallIntegerUsesSliderWhileLargeIntegerUsesValidatedInput() {
        MapRoomSettingInfo small = MapRoomQueryService.settingInfo(
                Setting.of("roundLimit", 10), true, "cs");
        MapRoomSettingInfo large = MapRoomQueryService.settingInfo(
                Setting.of("readyStartTime", 200), true, "cs");

        assertEquals(MapRoomSettingInfo.SettingType.INTEGER, small.type());
        assertTrue(small.slider());
        assertEquals(1.0, small.step());
        assertEquals(20.0, small.maxValue());
        assertFalse(large.slider());
    }

    @Test
    void booleansAndStringsKeepButtonAndTextControlTypes() {
        MapRoomSettingInfo bool = MapRoomQueryService.settingInfo(
                Setting.of("teammateGlow", false), true, "cs");
        MapRoomSettingInfo text = MapRoomQueryService.settingInfo(
                Setting.of("displayName", "Arena"), true, "cs");

        assertEquals(MapRoomSettingInfo.SettingType.BOOLEAN, bool.type());
        assertFalse(bool.slider());
        assertEquals(MapRoomSettingInfo.SettingType.STRING, text.type());
        assertFalse(text.slider());
    }

    @Test
    void legacySettingFactoryUsesDefaultCategory() {
        Setting<Integer> setting = Setting.of("roundLimit", 10);
        MapRoomSettingInfo info = MapRoomQueryService.settingInfo(setting, true, "cs");

        assertEquals(Setting.DEFAULT_CATEGORY, setting.getCategory());
        assertEquals(Setting.DEFAULT_CATEGORY, info.category());
        assertEquals("setting.fpsm.category.default", info.categoryTranslationKey());
    }
}
