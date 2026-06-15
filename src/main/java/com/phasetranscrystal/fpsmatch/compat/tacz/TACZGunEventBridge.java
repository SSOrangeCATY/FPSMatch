package com.phasetranscrystal.fpsmatch.compat.tacz;

import com.phasetranscrystal.fpsmatch.common.event.*;
import com.tacz.guns.api.event.common.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * TACZ 事件 → FPSMatch 自定义事件 桥接器。
 * 仅在 TACZ 模组加载时由 {@link TACZBootstrap} 手动注册到 {@link MinecraftForge#EVENT_BUS}。
 * <p>
 * 当存在多个枪械模组同时加载时，各模组的 EventBridge 独立运行，互不干扰。
 * 核心代码通过 {@link GunCompatManager#findProvider} 路由到正确的 Provider。
 * </p>
 * <p>
 * 注意：此类不使用 {@code @Mod.EventBusSubscriber} 自动注册，
 * 而是由 TACZBootstrap 在确认 TACZ 已加载后手动调用
 * {@code MinecraftForge.EVENT_BUS.register(TACZGunEventBridge.class)}。
 * 其他枪械模组的 EventBridge 也应遵循此模式。
 * </p>
 */
public class TACZGunEventBridge {

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity shooter = event.getShooter();
        MinecraftForge.EVENT_BUS.post(new FPSMGunFireEvent(shooter));
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity entity = event.getEntity();
        MinecraftForge.EVENT_BUS.post(new FPSMGunReloadEvent(entity, event.getGunItemStack()));
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity shooter = event.getShooter();
        MinecraftForge.EVENT_BUS.post(new FPSMGunShootEvent(shooter));
    }

    @SubscribeEvent
    public static void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity dead = event.getKilledEntity();
        LivingEntity attacker = event.getAttacker();
        if (dead == null || attacker == null) return;
        ItemStack gunStack = attacker.getMainHandItem();
        MinecraftForge.EVENT_BUS.post(new FPSMGunKillEvent(
                attacker, dead, event.isHeadShot(), event.getBullet(), gunStack));
    }

    @SubscribeEvent
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        LivingEntity hurtEntity = null;
        if (event.getHurtEntity() instanceof LivingEntity he) {
            hurtEntity = he;
        }
        if (hurtEntity == null) return;
        FPSMGunDamageEvent fpsmEvent = new FPSMGunDamageEvent(
                hurtEntity, event.getBaseAmount(), event.isHeadShot());
        MinecraftForge.EVENT_BUS.post(fpsmEvent);
        event.setBaseAmount(fpsmEvent.getBaseAmount());
        event.setHeadshotMultiplier(fpsmEvent.getHeadshotMultiplier());
    }
}