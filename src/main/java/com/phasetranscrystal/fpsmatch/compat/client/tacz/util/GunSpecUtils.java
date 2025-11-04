package com.phasetranscrystal.fpsmatch.compat.client.tacz.util;

import com.phasetranscrystal.fpsmatch.compat.client.tacz.fakeitem.ClientFakeItemManager;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

public final class GunSpecUtils {
    // 动画触发常量
    public static final String TRIGGER_RELOAD = "reload";
    public static final String TRIGGER_CANCEL_RELOAD = "cancel_reload";
    public static final String TRIGGER_RELOAD_END = "reload_end";
    public static final String TRIGGER_STOP_RELOAD = "stop_reload";
    public static final String TRIGGER_SHOOT = "input_shoot";
    public static final String TRIGGER_INSPECT = "input_inspect";
    public static final String TRIGGER_CANCEL_INSPECT = "cancel_inspect";
    public static final String TRIGGER_STOP_INSPECT = "stop_inspect";
    public static final String TRIGGER_BOLT = "input_bolt";

    private GunSpecUtils() {}

    /**
     * 安全触发动画状态机
     */
    public static boolean safeTrigger(AnimationStateMachine<?> asm, String trigger) {
        try {
            Method method = asm.getClass().getMethod("trigger", String.class);
            method.invoke(asm, trigger);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 获取当前渲染的物品栈（优先假物品，其次目标实体物品）
     */
    public static ItemStack getCurrentRenderStack(LivingEntity spectated) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && ClientFakeItemManager.isHoldingFakeItem()) {
            return ClientFakeItemManager.getCurrentFakeStack();
        }
        return spectated != null ? spectated.getMainHandItem() : ItemStack.EMPTY;
    }

    /**
     * 检查是否仍在旁观目标实体
     */
    public static boolean isStillSpectating(Player self, LivingEntity target) {
        if (self == null || !self.isSpectator() || target == null) return false;
        return Minecraft.getInstance().getCameraEntity() == target;
    }
}