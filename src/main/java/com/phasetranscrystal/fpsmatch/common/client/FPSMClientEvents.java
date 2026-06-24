package com.phasetranscrystal.fpsmatch.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderableArea;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderablePoint;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.TriState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Collection;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class FPSMClientEvents
{
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) {
            return;
        }
        if (!FPSMClient.getGlobalData().isMapSelectionButtonVisible()) {
            return;
        }
        event.addListener(Button.builder(Component.translatable("gui.fpsm.map_select.open"), button -> FPSMatch.sendToServer(new OpenMapSelectionC2SPacket()))
                .pos(event.getScreen().width / 2 - 102, event.getScreen().height - 32 - 20)
                .size(204, 20)
                .build());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
            mc.getSoundManager().stop();
        }
    }

    @SubscribeEvent
    public static void onCanRenderNameTag(RenderNameTagEvent.CanRender event) {
        if (FPSMConfig.Server.disableRenderNameTag.get()) {
            event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Collection<RenderableArea> areas = FPSMClient.getGlobalData().getDebugData().getAreas();
        Collection<RenderablePoint> points = FPSMClient.getGlobalData().getDebugData().getPoints();
        if (areas.isEmpty() && points.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        float lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;

        poseStack.pushPose();

        try {
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            for (RenderableArea renderable : areas) {
                renderable.submit(poseStack, event.getSubmitNodeCollector(), lineWidth);
            }

            for (RenderablePoint renderable : points) {
                renderable.submit(poseStack, event.getSubmitNodeCollector(), lineWidth);
            }
        } finally {
            poseStack.popPose();
        }
    }

}
