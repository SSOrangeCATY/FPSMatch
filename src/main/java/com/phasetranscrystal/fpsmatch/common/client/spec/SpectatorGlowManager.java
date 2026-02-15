package com.phasetranscrystal.fpsmatch.common.client.spec;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class SpectatorGlowManager {

    private static final Set<LivingEntity> FAKE_GLOW_ENTITIES = new HashSet<>();
    private static boolean inGlowLogic = false;

    public static boolean shouldGlow(LivingEntity entity) {
        if (inGlowLogic) return false;

        inGlowLogic = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            FPSMClientGlobalData data = FPSMClient.getGlobalData();
            boolean isSpecTeam = data.getCurrentClientTeam().map(ClientTeam::isSpectator).orElse(false);

            if (mc.player == null || !mc.player.isSpectator()) return false;

            if (entity instanceof Player target && (!data.isSameTeam(mc.player, target) || !isSpecTeam)) {
                return false;
            }

            addFakeGlow(entity);
            return true;
        } finally {
            inGlowLogic = false;
        }
    }

    private static void addFakeGlow(LivingEntity entity) {
        if (FAKE_GLOW_ENTITIES.add(entity)) {
            entity.setGlowingTag(true);
        }
    }

    public static void clearFakeGlowFromAll() {
        for (LivingEntity e : FAKE_GLOW_ENTITIES) {
            if (e != null && e.isAlive() && !e.hasEffect(MobEffects.GLOWING)) {
                e.setGlowingTag(false);
            }
        }
        FAKE_GLOW_ENTITIES.clear();
    }

}