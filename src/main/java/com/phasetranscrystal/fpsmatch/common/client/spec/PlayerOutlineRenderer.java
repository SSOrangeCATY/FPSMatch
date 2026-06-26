package com.phasetranscrystal.fpsmatch.common.client.spec;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PlayerOutlineRenderer {
    public static final int NO_OUTLINE_COLOR = -1;

    private static final int TEAM_RED = 64;
    private static final int TEAM_GREEN = 160;
    private static final int TEAM_BLUE = 255;
    private static final int ENEMY_RED = 255;
    private static final int ENEMY_GREEN = 64;
    private static final int ENEMY_BLUE = 64;
    private static final int SPECTATOR_RED = 255;
    private static final int SPECTATOR_GREEN = 220;
    private static final int SPECTATOR_BLUE = 64;

    private static final int TEAM_COLOR = rgb(TEAM_RED, TEAM_GREEN, TEAM_BLUE);
    private static final int ENEMY_COLOR = rgb(ENEMY_RED, ENEMY_GREEN, ENEMY_BLUE);
    private static final int SPECTATOR_COLOR = rgb(SPECTATOR_RED, SPECTATOR_GREEN, SPECTATOR_BLUE);

    private PlayerOutlineRenderer() {
    }

    public static boolean shouldOutline(Entity target) {
        return getOutlineColor(target) != NO_OUTLINE_COLOR;
    }

    public static int getOutlineColor(Entity target) {
        if (!(target instanceof Player player)) return NO_OUTLINE_COLOR;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null || player == localPlayer) return NO_OUTLINE_COLOR;

        if (SpectatorGlowManager.shouldGlow(player)) {
            return SPECTATOR_COLOR;
        }

        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        boolean localInNormalTeam = data.getCurrentClientTeam().map(ClientTeam::isNormal).orElse(false);
        if (!localInNormalTeam) return NO_OUTLINE_COLOR;

        boolean sameTeam = data.isSameTeam(localPlayer, player);
        if (sameTeam && data.isTeamGlow()) {
            return TEAM_COLOR;
        }
        if (!sameTeam && data.isEnemyGlow()) {
            return ENEMY_COLOR;
        }
        return NO_OUTLINE_COLOR;
    }

    private static int rgb(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }
}
