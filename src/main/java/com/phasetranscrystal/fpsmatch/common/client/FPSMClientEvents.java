package com.phasetranscrystal.fpsmatch.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderableArea;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class FPSMClientEvents
{
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS.get())) {
            mc.getSoundManager().stop();
        }
    }

    @SubscribeEvent
    public static void onLevelRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

        List<RenderableArea> areas = FPSMClient.getGlobalData().getDebugData().getAreas();
        if (areas.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();

        try {
            Camera camera = event.getCamera();
            Vec3 cameraPos = camera.getPosition();

            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            for (RenderableArea renderable : areas) {
                renderable.area().renderArea(poseStack, bufferSource);
            }

            bufferSource.endBatch();
        } finally {
            poseStack.popPose();
        }
    }

}
