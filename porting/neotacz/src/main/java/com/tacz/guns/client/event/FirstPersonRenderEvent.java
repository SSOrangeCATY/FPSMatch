package com.tacz.guns.client.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.renderer.item.TaczItemRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class FirstPersonRenderEvent {
    private static AnimationStateMachine<?> lastStateMachine = null;

//    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (stack.getItem() instanceof IGun) {
                event.setCanceled(true);
            }
            return;
        }
        // 事件事件给的是被延长渲染修改过后的物品，不是玩家实际手持的
        ItemStack stack = event.getItemStack();

        // 获取 TransformType
        ItemDisplayContext transformType;
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            transformType = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        } else {
            transformType = ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        }

        // 渲染相关内容由 TACZ 客户端 renderer registry 持有，避免依赖已移除的旧 item extension renderer 入口。
        var rendererOptional = TaczItemRenderers.getAnimated(stack);
        if (rendererOptional.isPresent()) {
            var renderer = rendererOptional.get();
            // 如果旧的状态机已经不再使用且未正常退出，使其静默退出
            AnimationStateMachine<?> machine = renderer.getStateMachine(stack);
            if (machine != lastStateMachine) {
                if (lastStateMachine != null && lastStateMachine.isInitialized()) {
                    lastStateMachine.exit();
                }
                lastStateMachine = machine;
            }
            // 物品处于后台时，阻止状态机初始化
            boolean flag = ItemStack.matches(player.getMainHandItem(), stack);
            if (flag && renderer.needReInit(stack)) {
                renderer.tryInit(stack, player, event.getPartialTick());
            }

            renderer.renderFirstPerson(player, stack, transformType, event.getPoseStack(), event.getSubmitNodeCollector(),
                    event.getPackedLight(), event.getPartialTick());
            event.setCanceled(true);
        }
    }
}
