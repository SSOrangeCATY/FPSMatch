package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateMode;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateTarget;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorCameraController;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SpectatorTargetS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class SpectatorTargetClientHandler {
    private SpectatorTargetClientHandler() {}

    public static void handle(SpectatorTargetS2CPacket packet) {
        packet.applyClient();
        SpectatorCameraController.setAngles(packet.yaw(), packet.pitch());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SpectateTarget target = new SpectateTarget(packet.mode(), packet.entityId(), packet.anchor(), packet.yaw(), packet.pitch(), packet.orbitRadius());
        Entity entity = target.entityId() < 0 ? null : mc.level.getEntity(target.entityId());
        if (packet.mode() == SpectateMode.TEAMMATE || packet.mode() == SpectateMode.ATTACH) {
            mc.setCameraEntity(entity == null ? mc.player : entity);
        } else if (packet.mode().isRestricted()) {
            mc.setCameraEntity(mc.player);
        }
    }
}

