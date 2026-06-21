package com.phasetranscrystal.fpsmatch.common.client.spec;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpectatorGlowManager {

    public static boolean shouldGlow(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        boolean isSpecTeam = data.getCurrentClientTeam().map(ClientTeam::isSpectator).orElse(false);

        if (mc.player == null || !mc.player.isSpectator()) return false;

        return entity instanceof Player target && data.isSameTeam(mc.player, target) && isSpecTeam;
    }

    public static void clearFakeGlowFromAll() {
    }

}
