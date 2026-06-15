package com.phasetranscrystal.fpsmatch.compat.tacz;

import com.phasetranscrystal.fpsmatch.compat.gun.GunCompatManager;
import com.phasetranscrystal.fpsmatch.compat.spectate.tacz.*;
import com.phasetranscrystal.fpsmatch.compat.tacz.client.event.SpectatorEventHandler;
import net.minecraftforge.common.MinecraftForge;

/**
 * TACZ 兼容层注册入口。
 * 由 {@link com.phasetranscrystal.fpsmatch.FPSMatch#registerCompat()} 在确认 TACZ 已加载后调用。
 * <p>
 * 调用方须在调用前通过 {@code ModList.get().isLoaded("tacz")} 判断，
 * 避免 JVM 在 TACZ 未加载时解析本类及其 TACZ 依赖，导致 NoClassDefFoundError。
 * </p>
 */
public final class TACZBootstrap {

    private TACZBootstrap() {}

    /**
     * 注册所有 TACZ 兼容层组件。
     * 调用方保证 TACZ 已加载。
     */
    public static void registerCompat() {
        // 注册 Provider
        GunCompatManager.register(new TACZGunProvider());
        // 注册事件桥接器
        MinecraftForge.EVENT_BUS.register(TACZGunEventBridge.class);
        // 注册旁观者事件类
        MinecraftForge.EVENT_BUS.register(SpectatorEventHandler.class);
        MinecraftForge.EVENT_BUS.register(SpectatorGunMovementMirror.class);
        MinecraftForge.EVENT_BUS.register(SpectatorGunRecoil.class);
        MinecraftForge.EVENT_BUS.register(SpectatorGunFireMirror.class);
        MinecraftForge.EVENT_BUS.register(SpectatorGunItemMirrorTicker.class);
        MinecraftForge.EVENT_BUS.register(SpectatorGunInspect.class);
        MinecraftForge.EVENT_BUS.register(OtherPlayerReloadSound.class);
    }
}