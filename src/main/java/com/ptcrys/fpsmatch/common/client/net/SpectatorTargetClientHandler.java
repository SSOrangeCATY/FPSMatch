package com.ptcrys.fpsmatch.common.client.net;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import com.ptcrys.fpsmatch.common.client.spec.SpectateMode;
import com.ptcrys.fpsmatch.common.client.spec.SpectateState;
import com.ptcrys.fpsmatch.common.client.spec.SpectateTarget;
import com.ptcrys.fpsmatch.common.client.spec.SpectatorCameraController;
import com.ptcrys.fpsmatch.common.packet.spec.SpectatorTargetS2CPacket;

public final class SpectatorTargetClientHandler {

    private SpectatorTargetClientHandler() {}

    public static void handle(SpectatorTargetS2CPacket packet) {
        // 同目标重复包(服务端周期性重发)不覆盖玩家已拖动的环绕角度，否则会"回弹/锁死"
        boolean orbitMode = packet.mode() == SpectateMode.C4_ORBIT || packet.mode() == SpectateMode.DEATH_SPOT;
        SpectateTarget current = SpectateState.getTarget();
        boolean sameTarget = current != null && current.mode() == packet.mode() && current.entityId() == packet.entityId() && current.anchor().distanceToSqr(packet.anchor()) < 4.0;
        if (!sameTarget || !orbitMode) {
            // 新目标/切换目标：采用服务端提供的初始姿态
            SpectatorCameraController.setAngles(packet.yaw(), packet.pitch());
        }
        packet.applyClient();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SpectateTarget target = new SpectateTarget(packet.mode(), packet.entityId(), packet.anchor(), packet.yaw(), packet.pitch(), packet.orbitRadius());
        Entity entity = target.entityId() < 0 ? null : mc.level.getEntity(target.entityId());
        if (packet.mode() == SpectateMode.TEAMMATE || packet.mode() == SpectateMode.ATTACH) {
            // 目标队友实体可能尚未在客户端加载完成；此时保持当前相机，不要强行切回自己，
            // 否则会立刻从队友身上脱落。服务端会在后续 tick 持续重发附着指令完成补挂。
            if (entity != null) {
                mc.setCameraEntity(entity);
            }
        } else if (packet.mode().isRestricted()) {
            mc.setCameraEntity(mc.player);
        }
    }
}
