package com.phasetranscrystal.fpsmatch.common.client.spec;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerOutlineRenderer {
    private static final int TEAM_RED = 64;
    private static final int TEAM_GREEN = 160;
    private static final int TEAM_BLUE = 255;
    private static final int ENEMY_RED = 255;
    private static final int ENEMY_GREEN = 64;
    private static final int ENEMY_BLUE = 64;
    private static final int SPECTATOR_RED = 255;
    private static final int SPECTATOR_GREEN = 220;
    private static final int SPECTATOR_BLUE = 64;
    private static final int OUTLINE_ALPHA = 255;
    private static boolean renderingOutline;

    private PlayerOutlineRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (renderingOutline || !(event.getEntity() instanceof AbstractClientPlayer target)) return;
        OutlineColor color = getOutlineColor(target);
        if (color == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        OutlineBufferSource outlineBufferSource = minecraft.renderBuffers().outlineBufferSource();
        outlineBufferSource.setColor(color.red, color.green, color.blue, OUTLINE_ALPHA);

        renderingOutline = true;
        try {
            event.getRenderer().render(target, target.getYRot(), event.getPartialTick(), event.getPoseStack(), outlineBufferSource, event.getPackedLight());
            outlineBufferSource.endOutlineBatch();
        } finally {
            renderingOutline = false;
        }
    }

    private static OutlineColor getOutlineColor(AbstractClientPlayer target) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null || target == localPlayer) return null;

        if (SpectatorGlowManager.shouldGlow(target)) {
            return new OutlineColor(SPECTATOR_RED, SPECTATOR_GREEN, SPECTATOR_BLUE);
        }

        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        boolean localInNormalTeam = data.getCurrentClientTeam().map(ClientTeam::isNormal).orElse(false);
        if (!localInNormalTeam) return null;

        boolean sameTeam = data.isSameTeam(localPlayer, target);
        if (sameTeam && data.isTeamGlow()) {
            return new OutlineColor(TEAM_RED, TEAM_GREEN, TEAM_BLUE);
        }
        if (!sameTeam && data.isEnemyGlow()) {
            return new OutlineColor(ENEMY_RED, ENEMY_GREEN, ENEMY_BLUE);
        }
        return null;
    }

    private record OutlineColor(int red, int green, int blue) {
    }
}
