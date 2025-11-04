package com.phasetranscrystal.fpsmatch.common.spectator;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.util.FPSMFormatUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public final class DamagePosTracker {

    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<UUID, Vec3> LAST_VICTIM_EYE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_TIME_NS    = new ConcurrentHashMap<>();
    private static final long TTL_NS = 10_000_000_000L; // 10s

    private DamagePosTracker(){}

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent e){
        LivingEntity ent = e.getEntity();
        if (!(ent instanceof ServerPlayer victim)) return;
        if (victim.level().isClientSide) return;

        Vec3 vEye = victim.getEyePosition(1.0F);
        LAST_VICTIM_EYE.put(victim.getUUID(), vEye);
        long now = System.nanoTime();
        LAST_TIME_NS.put(victim.getUUID(), now);

        LOG.info("[KillCamS][Track] hurt victim='{}' at A(victimEye)=({},{},{})",
                victim.getGameProfile().getName(), FPSMFormatUtil.fmt2(vEye.x), FPSMFormatUtil.fmt2(vEye.y), FPSMFormatUtil.fmt2(vEye.z));
    }

    /**
     * 消耗（获取并移除）受害者眼睛位置信息
     * 该方法用于从跟踪缓存中获取指定受害者最后一次记录的眼睛位置坐标，
     * 并在获取后立即从缓存中移除该数据。主要用于击杀回放等场景。
     *
     * @param victim 受害者玩家对象，不能为null
     * @return 如果存在未过期的受害者眼睛位置坐标，返回包含该坐标的Optional；
     *         如果victim为null、坐标不存在、数据已过期，返回空的Optional
     *
     * @apiNote 该方法会同时检查时间有效性，默认TTL为5秒
     * @implNote 使用纳秒时间戳进行高精度时间验证，数据过期后会自动清理
     * <p>
     * 使用示例：
     * <pre>{@code
     * Optional<Vec3> eyePos = consumeVictimEye(victimPlayer);
     * if (eyePos.isPresent()) {
     *     // 使用眼睛坐标进行击杀回放渲染
     *     Vec3 position = eyePos.get();
     * }
     * }</pre>
     */
    public static Optional<Vec3> consumeVictimEye(ServerPlayer victim){
        if (victim == null) return Optional.empty();
        UUID id = victim.getUUID();
        Vec3 v = LAST_VICTIM_EYE.remove(id);
        Long t = LAST_TIME_NS.remove(id);
        if (v == null || t == null) return Optional.empty();
        long now = System.nanoTime();
        if (now - t > TTL_NS) {
            LOG.info("[KillCamS][Track] last damage pos expired: victim='{}' age={}ms",
                    victim.getGameProfile().getName(), (now - t)/1_000_000.0);
            return Optional.empty();
        }
        return Optional.of(v);
    }

}