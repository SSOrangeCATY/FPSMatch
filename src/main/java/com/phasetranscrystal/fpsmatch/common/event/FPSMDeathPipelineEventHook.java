package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.DeathContext;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.phasetranscrystal.fpsmatch.compat.IPassThroughEntity;
import com.phasetranscrystal.fpsmatch.compat.gun.GunCompatManager;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 对局内伤害/击杀/死亡代理管线。
 * <p>
 * 核心约束：
 * <ul>
 *     <li>对局内所有击杀<strong>不实际触发原版死亡结算</strong>。</li>
 *     <li>原版 {@link LivingDeathEvent} 在对局中会被取消，由本管线代理后续结算。</li>
 *     <li>结算入口统一收敛到 {@link #finalizeDeath(BaseMap, DeathContext)}。</li>
 *     <li>枪械特有信息（爆头、子弹实体等）通过 {@link FPSMGunKillEvent} 进行延迟补全。</li>
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
     * 本 tick END 需要结算的死亡上下文。
     * 所有读写均发生在服务端主线程，使用普通 HashMap 即可。
     */
    private static final Map<UUID, PendingDeath> readyDeaths = new HashMap<>();

    /**
     * 暂存的枪械击杀详情（FPSMGunKillEvent 在 LivingDeathEvent 之前触发，需要暂存）。
     * 所有读写均发生在服务端主线程，使用普通 HashMap 即可。
     */
    private static final Map<UUID, GunKillDetail> pendingGunKills = new HashMap<>();

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
        if (!(event.getEntity() instanceof ServerPlayer hurt)) return;

        Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(hurt);
        if (opt.isEmpty()) return;
        BaseMap map = opt.get();
        if (!map.isStart()) return;

        if (hurt.isSpectator() || isRecentlyKilled(hurt.getUUID())) {
            event.setCanceled(true);
            return;
        }

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

    /**
     * 死亡入口（代理原版死亡）：
     * <ul>
     *     <li>构建 {@link DeathContext} 并放入待结算缓存。</li>
     *     <li>将结算推迟 1 tick，等待枪械事件补全更多击杀细节。</li>
     *     <li>取消原版死亡事件，避免原版直接处理死亡。</li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isEmpty()) return;

            BaseMap map = opt.get();
            if (map.isStart()) {
                // 已死亡进入旁观者或本 tick 已被结算过的玩家不再重复处理
                if (player.isSpectator() || isRecentlyKilled(player.getUUID())) {
                    event.setCanceled(true);
                    return;
                }
                // 对局内击杀/死亡统一由管线代理结算，阻止原版死亡落地
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                RECENTLY_KILLED.add(player.getUUID());

                FPSMapEvent.PlayerEvent.DeathEvent deathEvent = new FPSMapEvent.PlayerEvent.DeathEvent(map, player, event.getSource());
                MinecraftForge.EVENT_BUS.post(deathEvent);
                if (deathEvent.isCanceled()) {
                    return;
                }

                ServerPlayer attacker = deathEvent.getAttacker().orElse(null);
                ItemStack deathItem = map.resolveDeathItem(attacker, deathEvent.getSource());
                DeathContext context = new DeathContext(player, attacker, deathEvent.getSource(), deathItem, player.serverLevel().getGameTime());

                readyDeaths.put(player.getUUID(), new PendingDeath(map, context));
            }
            return;
        }

        if (event.getEntity() instanceof Player player && player.level().isClientSide
                && FPSMCore.getInstance().getMapByPlayer(player).isPresent()) {
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

        if (!(event.getAttacker() instanceof ServerPlayer attacker)) return;

        if (FPSMCore.getInstance().getMapByPlayer(attacker).orElse(null) != map) return;

        ItemStack gunStack = event.getGunItemStack();
        // 即使 gunStack 不再是枪械（延迟击杀 / 攻击者切物品 / 枪械被丢弃），
        // 本事件本身仍代表一次枪械击杀，必须继续走兜底，确保 CSGameMap.handleDeath() 被调用。
        boolean recognizedGun = GunCompatManager.isGun(gunStack);
        ItemStack deathItem = recognizedGun ? gunStack : map.resolveDeathItem(attacker, deadPlayer.getLastDamageSource());

        boolean passWall = false;
        boolean passSmoke = false;
        boolean scopedKill = false;
        if (event.getBullet() instanceof IPassThroughEntity passThroughEntity) {
            passWall = passThroughEntity.fpsmatch$isWall();
            passSmoke = passThroughEntity.fpsmatch$isSmoke();
            scopedKill = passThroughEntity.fpsmatch$isScoped();
        }
        GunKillDetail detail = new GunKillDetail(
                event.isHeadShot(),
                event.getBullet(),
                attacker,
                deathItem,
                passWall,
                passSmoke,
                scopedKill
        );
        pendingGunKills.put(deadPlayer.getUUID(), detail);
        PendingDeath pendingDeath = readyDeaths.get(deadPlayer.getUUID());
        if (pendingDeath != null) {
            applyGunKillDetail(pendingDeath.context(), detail);
        }

        // 兜底：如果 LivingDeathEvent 未到达 FPSM（例如被 TACZ 或其他模组提前取消），
        // 确保死者进入 readyDeaths，否则 death 结算管线不会执行地图的 handleDeath()
        readyDeaths.computeIfAbsent(deadPlayer.getUUID(), uuid -> {
            deadPlayer.setHealth(deadPlayer.getMaxHealth());
            RECENTLY_KILLED.add(deadPlayer.getUUID());
            DamageSource source = deadPlayer.getLastDamageSource() != null
                    ? deadPlayer.getLastDamageSource()
                    : deadPlayer.damageSources().generic();
            DeathContext context = new DeathContext(
                    deadPlayer,
                    attacker,
                    source,
                    deathItem,
                    deadPlayer.serverLevel().getGameTime()
            );
            applyGunKillDetail(context, detail);
            return new PendingDeath(map, context);
        });
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

        // 补全枪械击杀详情（FPSMGunKillEvent 在 LivingDeathEvent 之前触发）
        GunKillDetail gunKill = pendingGunKills.remove(player.getUUID());
        if (gunKill != null) {
            applyGunKillDetail(context, gunKill);
        }

        ServerPlayer killer = context.getAttacker();

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

    private static void applyGunKillDetail(DeathContext context, GunKillDetail gunKill) {
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

    /**
     * 待结算死亡记录。
     *
     * @param map     所属地图
     * @param context 死亡上下文（可被枪械事件补全）
     */
    private record PendingDeath(BaseMap map, DeathContext context) {
    }

    /**
     * 暂存的枪械击杀详情（FPSMGunKillEvent 在 LivingDeathEvent 之前触发，
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
