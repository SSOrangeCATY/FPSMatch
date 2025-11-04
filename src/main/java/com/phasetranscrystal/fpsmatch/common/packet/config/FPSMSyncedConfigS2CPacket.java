package com.phasetranscrystal.fpsmatch.common.packet.config;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.config.FPSMSyncedConfig;
import com.phasetranscrystal.fpsmatch.core.data.PackedSetting;
import com.phasetranscrystal.fpsmatch.core.data.Setting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务器到客户端的同步配置包
 */
public record FPSMSyncedConfigS2CPacket(Map<String, PackedSetting<?>> settings) {

    public static FPSMSyncedConfigS2CPacket create() {
        Map<String, Setting<?>> syncedSettings = FPSMSyncedConfig.getAllSettings();
        Map<String, PackedSetting<?>> packedSettings = new HashMap<>();

        syncedSettings.forEach((name, setting) -> {
            packedSettings.put(name, PackedSetting.fromSetting(setting));
        });

        return new FPSMSyncedConfigS2CPacket(packedSettings);
    }

    public static void encode(FPSMSyncedConfigS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.settings.size());
        packet.settings.forEach((name, packedSetting) -> {
            buf.writeUtf(name);
            FPSMSyncedConfig.getSetting(name).ifPresent(setting -> setting.writeToBuf(buf));
        });
    }

    public static FPSMSyncedConfigS2CPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, PackedSetting<?>> settings = new HashMap<>();

        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            FPSMSyncedConfig.getSetting(name).ifPresent(setting -> {
                Setting<?> tempSetting = createTempSetting(setting);
                tempSetting.readFromBuf(buf);
                settings.put(name, PackedSetting.fromSetting(tempSetting));
            });
        }

        return new FPSMSyncedConfigS2CPacket(settings);
    }

    @SuppressWarnings("unchecked")
    private static <T> Setting<T> createTempSetting(Setting<?> original) {
        return new Setting<>(
                original.getConfigName(),
                (Codec<T>) original.codec(),
                null
        );
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            FPSMSyncedConfig.updateSettings(this.settings);
        });
        context.setPacketHandled(true);
    }

}