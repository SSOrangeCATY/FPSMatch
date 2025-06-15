package com.phasetranscrystal.fpsmatch.common.net.register;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.net.*;
import com.phasetranscrystal.fpsmatch.common.net.cs.CSGameSettingsS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.CSGameTabStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.CSTabRemovalS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.DeathMessageS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.bomb.BombActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.bomb.BombActionS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.bomb.BombDemolitionProgressS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.mvp.MvpHUDCloseS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.mvp.MvpMessageS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.shop.*;
import com.phasetranscrystal.fpsmatch.common.net.effect.FlashBombAddonS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.entity.ThrowEntityC2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


public class NetworkPacketRegister {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    public static final Logger LOGGER = LoggerFactory.getLogger("FPSMatch NetWork Packet Register");
    public static void registerPackets() {
        registerPacket(CSGameSettingsS2CPacket.class);
        registerPacket(ShopDataSlotS2CPacket.class);
        registerPacket(ShopActionC2SPacket.class);
        registerPacket(BombActionC2SPacket.class);
        registerPacket(BombActionS2CPacket.class);
        registerPacket(BombDemolitionProgressS2CPacket.class);
        registerPacket(ShopMoneyS2CPacket.class);
        registerPacket(ShopStatesS2CPacket.class);
        registerPacket(CSGameTabStatsS2CPacket.class);
        registerPacket(FPSMatchStatsResetS2CPacket.class);
        registerPacket(DeathMessageS2CPacket.class);
        registerPacket(FPSMatchLoginMessageS2CPacket.class);
        registerPacket(ThrowEntityC2SPacket.class);
        registerPacket(FlashBombAddonS2CPacket.class);
        registerPacket(CSTabRemovalS2CPacket.class);
        registerPacket(FPSMatchGameTypeS2CPacket.class);
        registerPacket(MvpMessageS2CPacket.class);
        registerPacket(MvpHUDCloseS2CPacket.class);
        registerPacket(FPSMusicPlayS2CPacket.class);
        registerPacket(FPSMusicStopS2CPacket.class);
        registerPacket(SaveSlotDataC2SPacket.class);
        registerPacket(EditToolSelectMapC2SPacket.class);
        registerPacket(PullGameInfoC2SPacket.class);
        registerPacket(FPSMatchRespawnS2CPacket.class);
    }


    @SuppressWarnings("unchecked")
    private static <T> void registerPacket(Class<T> packetClass) {
        try {
            // 检查 encode
            Method encode = packetClass.getMethod("encode", packetClass, FriendlyByteBuf.class);
            if (!Modifier.isStatic(encode.getModifiers())) {
                throw new IllegalArgumentException("encode() must be static in " + packetClass.getName());
            }

            // 检查 decode
            Method decode = packetClass.getMethod("decode", FriendlyByteBuf.class);
            if (!Modifier.isStatic(decode.getModifiers())) {
                throw new IllegalArgumentException("decode() must be static in " + packetClass.getName());
            }
            if (!packetClass.isAssignableFrom(decode.getReturnType())) {
                throw new IllegalArgumentException("decode() must return " + packetClass.getName());
            }

            // 检查 handle
            Method handle = packetClass.getMethod("handle", Supplier.class);

            // 注册 Packet
            FPSMatch.INSTANCE.messageBuilder(packetClass, ID_COUNTER.getAndIncrement())
                    .encoder((packet, buf) -> {
                        try {
                            encode.invoke(null, packet, buf);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to encode packet", e);
                        }
                    })
                    .decoder(buf -> {
                        try {
                            return (T) decode.invoke(null, buf);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to decode packet", e);
                        }
                    })
                    .consumerNetworkThread((packet, ctx) -> {
                        try {
                            handle.invoke(packet, ctx);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to handle packet", e);
                        }
                    })
                    .add();
            // LOGGER.info("{} registered", packetClass.getSimpleName());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Packet class " + packetClass.getName() +
                    " is missing required methods (encode/decode/handle)", e);
        }
    }
}
