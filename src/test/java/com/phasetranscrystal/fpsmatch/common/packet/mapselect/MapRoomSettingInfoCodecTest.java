package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapRoomSettingInfoCodecTest {
    @Test
    void preservesPresentationMetadataAcrossNetworkRoundTrip() {
        MapRoomSettingInfo expected = new MapRoomSettingInfo(
                "minAssistDamageRatio",
                "0.25",
                "0.25",
                true,
                "setting.base.minAssistDamageRatio",
                MapRoomSettingInfo.SettingType.DECIMAL,
                "setting.base.minAssistDamageRatio.desc",
                true,
                0.0,
                1.0,
                0.01,
                "player"
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MapRoomSettingInfo.encode(expected, buffer);

        assertEquals(expected, MapRoomSettingInfo.decode(buffer));
    }
}
