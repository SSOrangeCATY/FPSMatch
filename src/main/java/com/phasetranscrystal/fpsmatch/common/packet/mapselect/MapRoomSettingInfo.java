package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import net.minecraft.network.FriendlyByteBuf;

public record MapRoomSettingInfo(String name, String value, String defaultValue, boolean editable) {
    private static final int NAME_MAX_LENGTH = 128;
    private static final int VALUE_MAX_LENGTH = 1024;

    public static void encode(MapRoomSettingInfo info, FriendlyByteBuf buf) {
        buf.writeUtf(info.name(), NAME_MAX_LENGTH);
        buf.writeUtf(info.value(), VALUE_MAX_LENGTH);
        buf.writeUtf(info.defaultValue(), VALUE_MAX_LENGTH);
        buf.writeBoolean(info.editable());
    }

    public static MapRoomSettingInfo decode(FriendlyByteBuf buf) {
        return new MapRoomSettingInfo(buf.readUtf(NAME_MAX_LENGTH), buf.readUtf(VALUE_MAX_LENGTH), buf.readUtf(VALUE_MAX_LENGTH), buf.readBoolean());
    }
}
