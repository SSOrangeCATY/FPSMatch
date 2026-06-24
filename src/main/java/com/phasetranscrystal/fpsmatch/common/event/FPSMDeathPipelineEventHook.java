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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * 鐎电懓鐪崘鍛縺鐎?閸戠粯娼?濮濊楠告禒锝囨倞缁狅紕鍤庨妴? * <p>
 * 閺嶇ǹ绺剧痪锔芥将閿? * <ul>
 *     <li>鐎电懓鐪崘鍛閺堝鍤弶鈧?strong>娑撳秴鐤勯梽鍛靶曢崣鎴濆斧閻楀牊顒存禍锛勭波缁?/strong>閵?/li>
 *     <li>閸樼喓澧?{@link LivingDeathEvent} 閸︺劌顕仦鈧稉顓濈窗鐞氼偄褰囧☉鍫礉閻㈣鲸婀扮粻锛勫殠娴狅絿鎮婇崥搴ｇ敾缂佹挾鐣婚妴?/li>
 *     <li>缂佹挾鐣婚崗銉ュ經缂佺喍绔撮弨鑸垫殐閸?{@link #finalizeDeath(BaseMap, DeathContext)}閵?/li>
 *     <li>閺嬵亝顫悧瑙勬箒娣団剝浼呴敍鍫㈠瀻婢舵番鈧礁鐡欏鐟扮杽娴ｆ挾鐡戦敍澶愨偓姘崇箖 {@link EntityKillByGunEvent} 鏉╂稖顢戝鎯扮箿鐞涖儱鍙忛妴?/li>
 * </ul>
 * 閸ョ姵顒濋敍宀?甯虹?硅泛婀?电懓鐪崘鍛偓婊嗩潶閸戠粯娼冮垾婵婎嚔娑斿绗傞弰顖滄暠缁狅紕鍤庢す鍗炲З閻ㄥ嫮濮搁幀浣界讣缁変紮绱濋懓灞肩瑝閺勵垰甯悧鍫燁劥娴溾剝绁︾粙瀣ㄢ偓? */
@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMDeathPipelineEventHook {


    /**
     * 閺?tick 閸愬懓顫︽禒锝囨倞濮濊楠搁惃鍕负鐎?UUID閿涘牏鏁ゆ禍搴に夐崗銊︾仚濮婃澘鍤弶鈧禍瀣╂閸掋倕鐣鹃敍澶堚偓?     */
    private static final Set<UUID> RECENTLY_KILLED = new HashSet<>();

    /**
     * 閺?tick END 闂団偓鐟曚胶绮ㄧ粻妤冩畱濮濊楠告稉濠佺瑓閺?     */
    private static final ConcurrentHashMap<UUID, PendingDeath> readyDeaths = new ConcurrentHashMap<>();

    /**
     * 閺嗗倸鐡ㄩ惃鍕仚濮婃澘鍤弶鈧拠锔藉剰閿涘湕ntityKillByGunEvent 閸?LivingDeathEvent 娑斿澧犵憴锕?褰傞敍宀勬付鐟曚焦娈忕?涙﹫绱?     */
    private static final ConcurrentHashMap<UUID, GunKillDetail> pendingGunKills = new ConcurrentHashMap<>();

    public static boolean isRecentlyKilled(UUID uuid) {
        return RECENTLY_KILLED.contains(uuid);
    }

    /**
     * 娴笺倕顔婇崗銉ュ經閿?     * <ul>
     *     <li>閸忓牐娴嗛崣鎴滆礋 {@link FPSMapEvent.PlayerEvent.HurtEvent} 娓氭稒膩瀵繐鐪伴弨鐟板晸/閸欐牗绉烽妴?/li>
     *     <li>閺堚偓缂佸牅婵?鐎瑰啿鈧壈鎯ょ?规艾鎮楅敍宀冪殶閻?{@link BaseMap#recordHurtData(ServerPlayer, net.minecraft.world.damagesource.DamageSource, float)} 鐠佹澘缍嶆导銈咁唺閺勫海绮忛妴?/li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer hurt) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(hurt);
            if (opt.isEmpty()) return;
            BaseMap map = opt.get();
            if (map.isStart()) {
                FPSMapEvent.PlayerEvent.HurtEvent hurtEvent = new FPSMapEvent.PlayerEvent.HurtEvent(map, hurt, event.getSource(), event.getAmount());

                if (NeoForge.EVENT_BUS.post(hurtEvent).isCanceled()) {
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
     * 濮濊楠搁崗銉ュ經閿涘牅鍞悶鍡楀斧閻楀牊顒存禍鈽呯礆閿?     * <ul>
     *     <li>閺嬪嫬缂?{@link DeathContext} 楠炶埖鏂侀崗銉ョ窡缂佹挾鐣荤紓鎾崇摠閵?/li>
     *     <li>鐏忓棛绮ㄧ粻妤佸腹鏉?1 tick閿涘瞼鐡戝鍛仚濮婇绨ㄦ禒鎯八夐崗銊︽纯婢舵艾鍤弶鈧紒鍡氬Ν閵?/li>
     *     <li>閸欐牗绉烽崢鐔哄濮濊楠告禍瀣╂閿涘矂浼╅崗宥呭斧閻楀牏娲块幒銉ヮ槱閻炲棙顒存禍掳鈧?/li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(player);
            if (opt.isEmpty()) return;

            BaseMap map = opt.get();
            if (map.isStart()) {
                // 鐎电懓鐪崘鍛毊閺夆偓/濮濊楠哥紒鐔剁閻㈣京顓哥痪澶稿敩閻炲棛绮ㄧ粻妤嬬礉闂冪粯顒涢崢鐔哄濮濊楠搁拃钘夋勾
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                RECENTLY_KILLED.add(player.getUUID());

                FPSMapEvent.PlayerEvent.DeathEvent deathEvent = new FPSMapEvent.PlayerEvent.DeathEvent(map, player, event.getSource());
                NeoForge.EVENT_BUS.post(deathEvent);
                if (deathEvent.isCanceled()) {
                    return;
                }

                Optional<ServerPlayer> optional = deathEvent.getAttacker();
                ServerPlayer attacker = optional.orElse(null);
                ItemStack deathItem = map.resolveDeathItem(attacker, deathEvent.getSource());
                DeathContext context = new DeathContext(player, attacker, deathEvent.getSource(), deathItem, ((ServerLevel) player.level()).getGameTime());

                readyDeaths.put(player.getUUID(), new PendingDeath(map, context));
            }
        }
        if(event.getEntity() instanceof Player player && player.level().isClientSide()){
            if (FPSMCore.getInstance().getMapByPlayer(player).isEmpty()) return;
            event.setCanceled(true);
        }
    }

    /**
     * 閺嬵亝顫崙缁樻絻鐞涖儱鍙忛崗銉ュ經閿?     * <ul>
     *     <li>鐠囥儰绨ㄦ禒璺鸿嫙闂堢偞澧嶉張澶嬵劥娴滐繝鍏樻导姘承曢崣鎴礄娴犲懏鐏欓崙鏄忕熅瀵板嫸绱氶妴?/li>
     *     <li>閻€劋绨悰銉ュ弿 {@link DeathContext} 閻ㄥ嫭鐏欏鎵矎閼哄偊绱伴悥鍡椼仈閺嶅洩顔囬妴浣哥摍瀵懓鐤勬担鎾扁偓浣规暰閸戞槒鈧懍淇婇幁顖樷偓?/li>
     *     <li>娑撳秴婀銈咁槱閻╁瓨甯撮崑姘付缂佸牏绮虹拋鈽呯礉缂佺喕顓哥紒鐔剁閸?finalize 闂冭埖顔岄幍褑顢戦妴?/li>
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

        // 閸忔粌绨抽敍姘洤閺?LivingDeathEvent 閺堫亜鍩屾潏?FPSM閿涘牅绶ユ俊鍌濐潶 TACZ 閹存牕鍙炬禒鏍佺紒鍕絹閸撳秴褰囧☉鍫礆閿?        // 绾喕绻氬鏄忊偓鍛扮箻閸?readyDeaths閿涘苯鎯侀崚?death 缂佹挾鐣荤粻锛勫殠娑撳秳绱伴幍褑顢?BaseMap.handleDeath()
        final GunKillDetail finalDetail = detail;
        readyDeaths.computeIfAbsent(deadPlayer.getUUID(), uuid -> {
            deadPlayer.setHealth(deadPlayer.getMaxHealth());
            DamageSource source = deadPlayer.getLastDamageSource() != null
                    ? deadPlayer.getLastDamageSource()
                    : deadPlayer.damageSources().generic();
            DeathContext context = new DeathContext(
                    deadPlayer,
                    attacker,
                    source,
                    finalDetail.deathItem(),
                    ((ServerLevel) deadPlayer.level()).getGameTime()
            );
            return new PendingDeath(map, context);
        });
    }

    /**
     * Tick 妞瑰崬濮╅惃鍕鏉╃喓绮ㄧ粻妤?娅掗妴?     * <p>
     * 閸?END 闂冭埖顔岄幒銊ㄧ箻 tick閿涘苯鑻熺亸鍡楀煂閺堢喓娈戝璁抽娑撳﹣绗呴弬鍥偓浣稿弳缂佺喍绔寸紒鎾剁暬閺傝纭堕妴?     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        if (readyDeaths.isEmpty() && pendingGunKills.isEmpty()) return;
        readyDeaths.forEach((uuid, pending) -> finalizeDeath(pending.map(), pending.context()));
        readyDeaths.clear();
        pendingGunKills.clear();
        RECENTLY_KILLED.clear();
    }

    /**
     * 鐎电懓鐪崘鍛劥娴滐紕绮烘稉鈧紒鎾剁暬閻愬箍鈧?     * <p>
     * 妞ゅ搫绨敍?     * <ol>
     *     <li>鐠嬪啰鏁?{@link BaseMap#handleDeath(DeathContext)} 閸愭瑥鍙嗛崷鏉挎禈/濡?崇础濮濊楠搁悩鑸碘偓浣碘偓?/li>
     *     <li>鐠侊紕鐣婚崙缁樻絻閵嗕胶鍨庢径鏉戝毊閺夆偓閵嗕礁濮弨鑽ょ埠鐠伮扳偓?/li>
     *     <li>閸欐垵绔?{@link FPSMapEvent.PlayerEvent.KillEvent}閵?/li>
     *     <li>閸欐垿鈧線鍣搁悽鐔朵繆閸欏嘲瀵橀妴?/li>
     * </ol>
     * <p>
     * 濞夈劍鍓伴敍姘崇箹閺勵垪鈧粈鍞悶鍡橆劥娴溾檧鈧繃娓剁紒鍫ｆ儰閻愮櫢绱辩?电懓鐪崘鍛瑝娓氭繆绂嗛崢鐔哄濮濊楠稿ù浣衡柤鐎瑰本鍨氭稉濠呭牚闁槒绶妴?     */
    private static void finalizeDeath(BaseMap map, DeathContext context) {
        ServerPlayer player = context.getDeadPlayer();
        MapTeams mapTeams = map.getMapTeams();

        // 鐞涖儱鍙忛弸顏咁潾閸戠粯娼冪拠锔藉剰閿涘湕ntityKillByGunEvent 閸?LivingDeathEvent 娑斿澧犵憴锕?褰傞敍?        GunKillDetail gunKill = pendingGunKills.remove(player.getUUID());
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

        ServerPlayer killer = context.getAttacker();

        map.handleDeath(context);

        if (killer != null) {
            boolean enemyKill = !mapTeams.isSameTeam(player, killer);
            if (enemyKill) {
                if (!NeoForge.EVENT_BUS.post(new FPSMapEvent.PlayerEvent.KillRecordEvent(map, killer, player, context.getDamageSource())).isCanceled()) {
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
            NeoForge.EVENT_BUS.post(killEvent);
        }

//        FPSMatch.sendToPlayer(player, new FPSMatchRespawnS2CPacket());
    }

    /**
     * 瀵板懐绮ㄧ粻妤侇劥娴溌ゎ唶瑜版洏鈧?     *
     * @param map     閹碘偓鐏炵偛婀撮崶?     * @param context 濮濊楠告稉濠佺瑓閺傚浄绱欓崣顖濐潶閺嬵亝顫禍瀣╂鐞涖儱鍙忛敍?     */
    private record PendingDeath(BaseMap map, DeathContext context) {
    }

    /**
     * 閺嗗倸鐡ㄩ惃鍕仚濮婃澘鍤弶鈧拠锔藉剰閿涘湕ntityKillByGunEvent 閸?LivingDeathEvent 娑斿澧犵憴锕?褰傞敍?     * 閺嗗倸鐡ㄩ崥搴℃躬 finalizeDeath 闂冭埖顔岀悰銉ュ弿閸?DeathContext閿涘鈧?     */
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
