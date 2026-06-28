package com.tacz.guns.client.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.renderer.item.TaczItemRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class TickAnimationEvent {
    @SubscribeEvent
    public static void tickAnimation(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(gunIndex -> {
            var animationStateMachine = gunIndex.getAnimationStateMachine();
            if (animationStateMachine == null) {
                return;
            }
            // 群组服切世界导致的特殊 BUG 处理，正常情况不会遇到此问题
            if (player.input == null) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
                return;
            }
            if (!player.isMovingSlowly() && player.isSprinting()) {
                // 如果玩家正在移动，播放移动动画，否则播放 idle 动画
                animationStateMachine.trigger(GunAnimationConstant.INPUT_RUN);
            } else if (!player.isMovingSlowly() && player.input.getMoveVector().length() > 0.01) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_WALK);
            } else {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
            }
        });
    }

    @SubscribeEvent
    public static void tickAnimation(RenderFrameEvent.Pre event) {
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        // 渲染相关内容由 TACZ 客户端 renderer registry 持有，避免依赖已移除的旧 item extension renderer 入口。
        TaczItemRenderers.getAnimated(mainHandItem).ifPresent(renderer -> {
            // 如果物品不一样了，先尝试初始化状态机
            if (renderer.needReInit(mainHandItem)) {
                renderer.tryInit(mainHandItem, player, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
            renderer.visualUpdate(mainHandItem);
        });
    }
}
