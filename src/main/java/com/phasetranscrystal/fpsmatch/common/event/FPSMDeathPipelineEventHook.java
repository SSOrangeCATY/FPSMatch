package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchRespawnS2CPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.DeathContext;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.phasetranscrystal.fpsmatch.compat.IPassThroughEntity;
import com.phasetranscrystal.fpsmatch.compat.gun.GunCompatManager;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * 对局内伤害/击杀/死亡代理管线。
 * <p>
 * 核心约束：
 * <ul>
 *     <li>对局内所有击杀<strong>不实际触发原版死亡结算</strong>。</li>
 *     <li>原版 {@link LivingDeathEvent} 在对局中会被取消，由本管线代理后续结算。</li>
 *     <li>结算入口统一收敛到 {@link #finalizeDeath(BaseMap, DeathContext)}。</li>
 *     <li>枪械特有信息（爆头、子弹实体等）通过 {@link EntityKillByGunEvent} 进行延迟补全。</li>
 * </ul>
 * 因此，玩家在对局内“被击杀”语义上是由管线驱动的状态迁移，而不是原版死亡流程。
 */
@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FPSMDeathPipelineEventHook {


    /**
     * 本 tick 内被代理死亡的玩家 UUID（用于补全枪械击杀事件判定）。
     */
    private static final Set<UUID> RECENTLY_KILLED = new HashSet<>();

    /**
     * 本 tick END 需要结算的死亡上下文
     */
    private static final ConcurrentHashMap<UUID, PendingDeath> readyDeaths = new ConcurrentHashMap<>();

    /**
     * 暂存的枪械击杀详情（EntityKillByGunEvent 在 LivingDeathEvent 之前触发，需要暂存）
     */
    private static final ConcurrentHashMap<UUID, GunKillDetail> pendingGunKills = new ConcurrentHashMap<>();

    public static boolean isRecentlyKilled(UUID uuid) {
        return RECENTLY_KILLED.contains(uuid);
    }

    /**
     * 伤害入口：
     * <ul>
     *     <li>先转发为 {@link FPSMapEvent.PlayerEvent.HurtEvent} 供模式层改写/取消。</li>
     *     <li>最终伤害值落定后，调用 {@link BaseMap#recordHurtData(ServerPlayer, net.minecraft.world.damagesource.DamageSource, float)} 记录伤害明细。</li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer hurt) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(hurt);
            if (opt.isEmpty()) return;
            BaseMap map = opt.get();
            if (map.isStart()) {
                FPSMapEvent.PlayerEvent.HurtEvent hurtEvent = new FPSMapEvent.PlayerEvent.HurtEvent(map, hurt, event.getSource(), event.getAmount());

                if (MinecraftForge.EVENT_BUS.post(hurtEvent)) {
                    event.setCanceled(true);
                    return;
                }

                event.setAmount(hurtEvent.getAmount());

                if (event.getAmount() <= 0) {
                    event.setCanceled(true);
                    return;
                }

                map.recordHurtData(hurt, event.getSource(), event.getAmount());
            }
        }
    }

    /**
     * 死亡入口（代理原版死亡）：
     * <ul>
     *     <li>构建 {@link DeathContext} 并放入待结算缓存。</li>
     *     <li>将结算推迟 1 tick，等待枪械事件补全更多击杀细节。</li>
     *     <li>取消原版死亡事件，避免原版直接处理死亡。</li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isEmpty()) return;

            BaseMap map = opt.get();
            if (map.isStart()) {
                // 对局内击杀/死亡统一由管线代理结算，阻止原版死亡落地
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                RECENTLY_KILLED.add(player.getUUID());

                FPSMapEvent.PlayerEvent.DeathEvent deathEvent = new FPSMapEvent.PlayerEvent.DeathEvent(map, player, event.getSource());
                MinecraftForge.EVENT_BUS.post(deathEvent);
                if (deathEvent.isCanceled()) {
                    return;
                }

                Optional<ServerPlayer> optional = deathEvent.getAttacker();
                ServerPlayer attacker = optional.orElse(null);
                ItemStack deathItem = map.resolveDeathItem(attacker, deathEvent.getSource());
                DeathContext context = new DeathContext(player, attacker, deathEvent.getSource(), deathItem, player.serverLevel().getGameTime());

                readyDeaths.put(player.getUUID(), new PendingDeath(map, context));
            }
        }
        if(event.getEntity() instanceof Player player && player.level().isClientSide){
            if (FPSMCore.getInstance().getMapByPlayer(player).isEmpty()) return;
            event.setCanceled(true);
        }
    }

    /**
     * 枪械击杀补全入口：
     * <ul>
     *     <li>该事件并非所有死亡都会触发（仅枪击路径）。</li>
     *     <li>用于补全 {@link DeathContext} 的枪械细节：爆头标记、子弹实体、攻击者信息。</li>
     *     <li>不在此处直接做最终统计，统计统一在 finalize 阶段执行。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onPlayerKillEvent(FPSMGunKillEvent event) {
        if (!(event.getKilledEntity() instanceof ServerPlayer deadPlayer)) {
            return;
        }

        Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(deadPlayer);
        if (mapOpt.isEmpty()) return;
        BaseMap map = mapOpt.get();
        if (!(event.getAttacker() instanceof ServerPlayer attacker) || !GunCompatManager.isGun(attacker.getMainHandItem())) return;
        if (!FPSMCore.getInstance().getMapByPlayer(attacker).map(m -> m.equals(map)).orElse(false)) return;

        GunKillDetail detail = new GunKillDetail(
                event.isHeadShot(),
                event.getBullet(),
                attacker,
                map.resolveDeathItem(attacker, deadPlayer.getLastDamageSource() != null ? deadPlayer.getLastDamageSource() : deadPlayer.damageSources().generic())
        );
        if (event.getBullet() instanceof IPassThroughEntity passThroughEntity) {
            detail = new GunKillDetail(
                    event.isHeadShot(),
                    event.getBullet(),
                    attacker,
                    detail.deathItem(),
                    passThroughEntity.fpsmatch$isWall(),
                    passThroughEntity.fpsmatch$isSmoke(),
                    passThroughEntity.fpsmatch$isScoped()
            );
        }
        pendingGunKills.put(deadPlayer.getUUID(), detail);
    }

    /**
     * Tick 驱动的延迟结算器。
     * <p>
     * 在 END 阶段推进 tick，并将到期的死亡上下文送入统一结算方法。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (readyDeaths.isEmpty() && pendingGunKills.isEmpty()) return;
        readyDeaths.forEach((uuid, pending) -> finalizeDeath(pending.map(), pending.context()));
        readyDeaths.clear();
        pendingGunKills.clear();
        RECENTLY_KILLED.clear();
    }

    /**
     * 对局内死亡统一结算点。
     * <p>
     * 顺序：
     * <ol>
     *     <li>调用 {@link BaseMap#handleDeath(DeathContext)} 写入地图/模式死亡状态。</li>
     *     <li>计算击杀、爆头击杀、助攻统计。</li>
     *     <li>发布 {@link FPSMapEvent.PlayerEvent.KillEvent}。</li>
     *     <li>发送重生信号包。</li>
     * </ol>
     * <p>
     * 注意：这是“代理死亡”最终落点；对局内不依赖原版死亡流程完成上述逻辑。
     */
    private static void finalizeDeath(BaseMap map, DeathContext context) {
        ServerPlayer player = context.getDeadPlayer();
        MapTeams mapTeams = map.getMapTeams();
        ServerPlayer killer = context.getAttacker();

        // 补全枪械击杀详情（EntityKillByGunEvent 在 LivingDeathEvent 之前触发）
        GunKillDetail gunKill = pendingGunKills.remove(player.getUUID());
        if (gunKill != null) {
            context.setGunKill(true);
            context.setHeadShot(gunKill.isHeadShot());
            context.setGunBullet(gunKill.bullet());
            context.setPassWall(gunKill.passWall());
            context.setPassSmoke(gunKill.passSmoke());
            context.setScopedKill(gunKill.scopedKill());
            if (context.getAttacker() == null) {
                context.setAttacker(gunKill.attacker());
                context.setDeathItem(gunKill.deathItem());
            }
        }

        map.handleDeath(context);

        if (killer != null) {
            boolean enemyKill = !mapTeams.isSameTeam(player, killer);
            if (enemyKill) {
                if (!MinecraftForge.EVENT_BUS.post(new FPSMapEvent.PlayerEvent.KillRecordEvent(map, killer, player, context.getDamageSource()))) {
                    mapTeams.getPlayerData(killer).ifPresent(PlayerData::addKill);
                    if (context.isHeadShot()) {
                        mapTeams.getPlayerData(killer).ifPresent(PlayerData::addHeadshotKill);
                    }
                }
            }

            FPSMUtil.calculateAssistPlayer(map, player, map.getMinAssistDamageRatio()).ifPresent(assistData -> {
                if (!killer.getUUID().equals(assistData.getOwner())) {
                    assistData.addAssist();
                }
            });

            FPSMapEvent.PlayerEvent.KillEvent killEvent = new FPSMapEvent.PlayerEvent.KillEvent(map, killer, player, context.getDamageSource());
            MinecraftForge.EVENT_BUS.post(killEvent);
        }

//        FPSMatch.sendToPlayer(player, new FPSMatchRespawnS2CPacket());
    }

    /**
     * 待结算死亡记录。
     *
     * @param map     所属地图
     * @param context 死亡上下文（可被枪械事件补全）
     */
    private record PendingDeath(BaseMap map, DeathContext context) {
    }

    /**
     * 暂存的枪械击杀详情（EntityKillByGunEvent 在 LivingDeathEvent 之前触发，
     * 暂存后在 finalizeDeath 阶段补全到 DeathContext）。
     */
    private record GunKillDetail(
            boolean isHeadShot,
            @Nullable Entity bullet,
            @Nullable ServerPlayer attacker,
            ItemStack deathItem,
            boolean passWall,
            boolean passSmoke,
            boolean scopedKill
    ) {
        GunKillDetail(boolean isHeadShot, @Nullable Entity bullet, @Nullable ServerPlayer attacker, ItemStack deathItem) {
            this(isHeadShot, bullet, attacker, deathItem, false, false, false);
        }
    }
}
